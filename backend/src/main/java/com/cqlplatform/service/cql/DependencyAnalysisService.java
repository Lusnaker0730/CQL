package com.cqlplatform.service.cql;

import com.cqlplatform.entity.CqlLibraryEntity;
import com.cqlplatform.repository.CqlLibraryRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DependencyAnalysisService {

    private final CqlLibraryRepository libraryRepository;
    private final com.cqlplatform.repository.TenantRepository tenantRepository;

    private static final Pattern DEP_PATTERN = Pattern.compile("^(.+?)\\s+version\\s+'([^']+)'$");

    /**
     * Effective tenant: the caller's, or the default tenant for legacy callers with none.
     * Dependency analysis runs on the request thread (invoked from CqlController), so the
     * tenant is read straight off {@link com.cqlplatform.security.TenantContext}. Scoping the
     * lookups keeps one clinic's dependency graph from resolving another clinic's libraries
     * once (tenant_id, name, version) allows same-named libraries across tenants (V62).
     */
    private Long effectiveTenantId() {
        Long tenantId = com.cqlplatform.security.TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository.findByCode("default")
                .map(com.cqlplatform.entity.TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
    }

    @Data
    @Builder
    public static class DependencyAnalysisResult {
        private List<DependencyInfo> dependencies;
        private List<VersionConflict> conflicts;
        private List<VersionMismatch> mismatches;
        private List<CircularDependency> circularDependencies;
        private boolean hasIssues;
    }

    @Data
    @Builder
    public static class DependencyInfo {
        private String name;
        private String declaredVersion;
        private String resolvedVersion;
        private boolean available;
        private boolean versionMatch;
        private List<DependencyInfo> transitiveDeps;
    }

    @Data
    @Builder
    public static class VersionConflict {
        private String libraryName;
        private List<String> requestedVersions;
        private List<String> requestedBy;
        private String resolvedVersion;
        private String severity;
    }

    @Data
    @Builder
    public static class VersionMismatch {
        private String libraryName;
        private String declaredVersion;
        private String availableVersion;
        private String requestedBy;
    }

    @Data
    @Builder
    public static class CircularDependency {
        private String libraryName;
        private String version;
        private List<String> cycle;
    }

    @Transactional(readOnly = true)
    public DependencyAnalysisResult analyze(String libraryId) {
        int dashIdx = libraryId.lastIndexOf('-');
        if (dashIdx < 1) {
            return emptyResult();
        }

        String name = libraryId.substring(0, dashIdx);
        String version = libraryId.substring(dashIdx + 1);

        Long tenantId = effectiveTenantId();
        Optional<CqlLibraryEntity> entityOpt = libraryRepository.findByTenantIdAndNameAndVersion(tenantId, name, version);
        if (entityOpt.isEmpty()) {
            return emptyResult();
        }

        CqlLibraryEntity entity = entityOpt.get();
        List<String> deps = entity.getDependencyList();
        if (deps == null || deps.isEmpty()) {
            return emptyResult();
        }

        List<DependencyInfo> depInfos = new ArrayList<>();
        List<VersionMismatch> mismatches = new ArrayList<>();
        List<CircularDependency> circularDeps = new ArrayList<>();
        Map<String, Map<String, List<String>>> versionRequests = new LinkedHashMap<>();

        String rootKey = name + "@" + version;
        LinkedHashSet<String> ancestorPath = new LinkedHashSet<>();
        ancestorPath.add(rootKey);

        for (String dep : deps) {
            DependencyInfo info = analyzeDependency(dep, name + " v" + version,
                    versionRequests, mismatches, circularDeps, ancestorPath, tenantId);
            depInfos.add(info);
        }

        List<VersionConflict> conflicts = detectConflicts(versionRequests);
        boolean hasIssues = !conflicts.isEmpty() || !mismatches.isEmpty() || !circularDeps.isEmpty();

        return DependencyAnalysisResult.builder()
                .dependencies(depInfos)
                .conflicts(conflicts)
                .mismatches(mismatches)
                .circularDependencies(circularDeps)
                .hasIssues(hasIssues)
                .build();
    }

    private DependencyInfo analyzeDependency(String dep, String requestedBy,
                                              Map<String, Map<String, List<String>>> versionRequests,
                                              List<VersionMismatch> mismatches,
                                              List<CircularDependency> circularDeps,
                                              LinkedHashSet<String> ancestorPath,
                                              Long tenantId) {
        Matcher matcher = DEP_PATTERN.matcher(dep.trim());
        String depName;
        String declaredVersion;

        if (matcher.matches()) {
            depName = matcher.group(1).trim().replace("\"", "");
            declaredVersion = matcher.group(2).trim();
        } else {
            depName = dep.split("\\s+")[0].replace("\"", "");
            declaredVersion = null;
        }

        versionRequests.computeIfAbsent(depName, k -> new LinkedHashMap<>())
                .computeIfAbsent(declaredVersion != null ? declaredVersion : "latest", k -> new ArrayList<>())
                .add(requestedBy);

        Optional<CqlLibraryEntity> found;
        if (declaredVersion != null) {
            found = libraryRepository.findByTenantIdAndNameAndVersion(tenantId, depName, declaredVersion);
            if (found.isEmpty()) {
                // Fall back to latest if exact version not found
                found = libraryRepository.findByTenantIdAndName(tenantId, depName).stream()
                        .max(Comparator.comparing(CqlLibraryEntity::getVersion, new SemanticVersionComparator()));
            }
        } else {
            found = libraryRepository.findByTenantIdAndName(tenantId, depName).stream()
                    .max(Comparator.comparing(CqlLibraryEntity::getVersion, new SemanticVersionComparator()));
        }

        String resolvedVersion = found.map(CqlLibraryEntity::getVersion).orElse(null);
        boolean available = found.isPresent();
        boolean versionMatch = available && (declaredVersion == null || declaredVersion.equals(resolvedVersion));

        if (available && !versionMatch) {
            mismatches.add(VersionMismatch.builder()
                    .libraryName(depName)
                    .declaredVersion(declaredVersion)
                    .availableVersion(resolvedVersion)
                    .requestedBy(requestedBy)
                    .build());
        }

        List<DependencyInfo> transitive = new ArrayList<>();
        String visitKey = depName + "@" + (resolvedVersion != null ? resolvedVersion : declaredVersion);

        if (ancestorPath.contains(visitKey)) {
            // Circular dependency detected — record the cycle path
            List<String> cyclePath = new ArrayList<>();
            boolean inCycle = false;
            for (String ancestor : ancestorPath) {
                if (ancestor.equals(visitKey)) inCycle = true;
                if (inCycle) cyclePath.add(ancestor);
            }
            cyclePath.add(visitKey); // close the cycle
            circularDeps.add(CircularDependency.builder()
                    .libraryName(depName)
                    .version(resolvedVersion != null ? resolvedVersion : declaredVersion)
                    .cycle(cyclePath)
                    .build());
            log.warn("Circular dependency detected: {}", String.join(" → ", cyclePath));
        } else if (found.isPresent()) {
            List<String> transDeps = found.get().getDependencyList();
            if (transDeps != null && !transDeps.isEmpty()) {
                // Clone ancestorPath for this branch so siblings get a complete view
                LinkedHashSet<String> childPath = new LinkedHashSet<>(ancestorPath);
                childPath.add(visitKey);
                for (String td : transDeps) {
                    transitive.add(analyzeDependency(td, depName + " v" + resolvedVersion,
                            versionRequests, mismatches, circularDeps, childPath, tenantId));
                }
            }
        }

        return DependencyInfo.builder()
                .name(depName)
                .declaredVersion(declaredVersion)
                .resolvedVersion(resolvedVersion)
                .available(available)
                .versionMatch(versionMatch)
                .transitiveDeps(transitive)
                .build();
    }

    private List<VersionConflict> detectConflicts(Map<String, Map<String, List<String>>> versionRequests) {
        List<VersionConflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<String>>> entry : versionRequests.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> versions = new ArrayList<>(entry.getValue().keySet());
                List<String> requestedBy = entry.getValue().values().stream()
                        .flatMap(List::stream)
                        .distinct()
                        .collect(Collectors.toList());

                String resolved = versions.stream()
                        .filter(v -> !"latest".equals(v))
                        .max(new SemanticVersionComparator())
                        .orElse(versions.get(0));

                boolean hasMajorDiff = hasMajorVersionDifference(versions);

                conflicts.add(VersionConflict.builder()
                        .libraryName(entry.getKey())
                        .requestedVersions(versions)
                        .requestedBy(requestedBy)
                        .resolvedVersion(resolved)
                        .severity(hasMajorDiff ? "error" : "warning")
                        .build());
            }
        }
        return conflicts;
    }

    private boolean hasMajorVersionDifference(List<String> versions) {
        Set<Integer> majors = new HashSet<>();
        for (String v : versions) {
            if ("latest".equals(v)) continue;
            try {
                majors.add(Integer.parseInt(v.split("\\.")[0]));
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return majors.size() > 1;
    }

    private DependencyAnalysisResult emptyResult() {
        return DependencyAnalysisResult.builder()
                .dependencies(List.of())
                .conflicts(List.of())
                .mismatches(List.of())
                .circularDependencies(List.of())
                .hasIssues(false)
                .build();
    }
}
