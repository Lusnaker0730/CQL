package com.cqlplatform.service.cql;

import org.cqframework.cql.cql2elm.LibrarySourceProvider;
import org.hl7.elm.r1.VersionedIdentifier;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

public class ClasspathLibrarySourceProvider implements LibrarySourceProvider {

    private final String basePath;

    public ClasspathLibrarySourceProvider(String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath : basePath + "/";
    }

    @Override
    public InputStream getLibrarySource(VersionedIdentifier libraryIdentifier) {
        String libraryName = libraryIdentifier.getId();
        String version = libraryIdentifier.getVersion();

        // Try with version first: LibraryName-version.cql
        String fileNameWithVersion = libraryName + "-" + version + ".cql";
        InputStream stream = tryLoadResource(fileNameWithVersion);
        if (stream != null) {
            return stream;
        }

        // Try without version: LibraryName.cql
        String fileNameWithoutVersion = libraryName + ".cql";
        return tryLoadResource(fileNameWithoutVersion);
    }

    private InputStream tryLoadResource(String fileName) {
        try {
            ClassPathResource resource = new ClassPathResource(basePath + fileName);
            if (resource.exists()) {
                return resource.getInputStream();
            }
        } catch (Exception e) {
            // Ignore and return null
        }
        return null;
    }
}
