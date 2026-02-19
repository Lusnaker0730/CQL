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

    private static final Pattern DEP_PATTERN = Pattern.compile("^(.+?)\\s+version\\s+'([^']+)'$");

    @Data
    @Builder
    public static class DependencyAnalysisResult {
        private List<DependencyInfo> dependencies;
        private List<VersionConflict> conflicts;
        private List<VersionMismatch> mismatches;
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

    @Transactional(readOnly = true)
    public DependencyAnalysisResult analyze(String libraryId) {
        int dashIdx = libraryId.lastIndexOf('-');
        if (dashIdx < 1) {
            return emptyResult();
        }

        String name = libraryId.substring(0, dashIdx);
        String version = libraryId.substring(dashIdx + 1);

        Optional<CqlLibraryEntity> entityOpt = libraryRepository.findByNameAndVersion(name, version);
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
        Map<String, Map<String, List<String>>> versionRequests = new LinkedHashMap<>();

        for (String dep : deps) {
            DependencyInfo info = analyzeDependency(dep, name + " v" + version, versionRequests, mismatches, new HashSet<>());
            depInfos.add(info);
        }

        List<VersionConflict> conflicts = detectConflicts(versionRequests);
        boolean hasIssues = !conflicts.isEmpty() || !mismatches.isEmpty();

        return DependencyAnalysisResult.builder()
                .dependencies(depInfos)
                .conflicts(conflicts)
                .mismatches(mismatches)
                .hasIssues(hasIssues)
                .build();
    }

    private DependencyInfo analyzeDependency(String dep, String requestedBy,
                                              Map<String, Map<String, List<String>>> versionRequests,
                                              List<VersionMismatch> mismatches,
                                              Set<String> visited) {
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
            found = libraryRepository.findByNameAndVersion(depName, declaredVersion);
            if (found.isEmpty()) {
                // Fall back to latest if exact version not found
                found = libraryRepository.findByName(depName).stream()
                        .max(Comparator.comparing(CqlLibraryEntity::getVersion, new SemanticVersionComparator()));
            }
        } else {
            found = libraryRepository.findByName(depName).stream()
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
        if (!visited.contains(visitKey) && found.isPresent()) {
            visited.add(visitKey);
            List<String> transDeps = found.get().getDependencyList();
            if (transDeps != null) {
                for (String td : transDeps) {
                    transitive.add(analyzeDependency(td, depName + " v" + resolvedVersion, versionRequests, mismatches, visited));
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
                .hasIssues(false)
                .build();
    }
}
