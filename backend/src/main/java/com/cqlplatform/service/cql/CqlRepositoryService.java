package com.cqlplatform.service.cql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides access to pre-built CQL library templates from the classpath repository.
 */
@Service
@Slf4j
public class CqlRepositoryService {

    private final CqlLibraryService libraryService;

    public CqlRepositoryService(CqlLibraryService libraryService) {
        this.libraryService = libraryService;
    }

    public record RepositoryLibrary(
            String name,
            String version,
            String description,
            String filename
    ) {}

    public List<RepositoryLibrary> listRepositoryLibraries() {
        List<RepositoryLibrary> libraries = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:cql-repository/*.cql");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                String name = extractLibraryName(content, filename);
                String version = extractLibraryVersion(content);
                String description = extractDescription(content);
                libraries.add(new RepositoryLibrary(name, version, description, filename));
            }
        } catch (IOException e) {
            log.warn("Failed to list repository libraries: {}", e.getMessage());
        }
        return libraries;
    }

    public String getRepositoryLibraryContent(String name) {
        try {
            // Try exact filename first
            ClassPathResource resource = new ClassPathResource("cql-repository/" + name + ".cql");
            if (resource.exists()) {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            }
            // Try with version in filename
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:cql-repository/" + name + "*.cql");
            if (resources.length > 0) {
                return resources[0].getContentAsString(StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("Failed to read repository library: {}", name, e);
        }
        throw new IllegalArgumentException("Repository library not found: " + name);
    }

    public com.cqlplatform.model.CqlLibrary importFromRepository(String name) {
        String content = getRepositoryLibraryContent(name);
        return libraryService.saveLibrary(content, "Imported from CQL repository");
    }

    private String extractLibraryName(String cql, String filename) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("library\\s+(\\S+)")
                .matcher(cql);
        if (m.find()) return m.group(1);
        return filename.replace(".cql", "");
    }

    private String extractLibraryVersion(String cql) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("version\\s+'([^']+)'")
                .matcher(cql);
        if (m.find()) return m.group(1);
        return "1.0.0";
    }

    private String extractDescription(String cql) {
        // Extract first comment block as description
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/\\*\\*?\\s*\\n?\\s*\\*?\\s*(.+?)\\s*\\*/",
                java.util.regex.Pattern.DOTALL).matcher(cql);
        if (m.find()) {
            String desc = m.group(1).replaceAll("\\s*\\*\\s*", " ").trim();
            if (desc.length() > 200) desc = desc.substring(0, 200) + "...";
            return desc;
        }
        return "Pre-built CQL library";
    }
}
