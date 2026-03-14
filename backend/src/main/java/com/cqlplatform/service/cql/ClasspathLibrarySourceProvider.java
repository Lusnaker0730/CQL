package com.cqlplatform.service.cql;

import kotlinx.io.CoreKt;
import kotlinx.io.JvmCoreKt;
import kotlinx.io.Source;
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
    public Source getLibrarySource(VersionedIdentifier libraryIdentifier) {
        String libraryName = libraryIdentifier.getId();
        String version = libraryIdentifier.getVersion();

        // Try with version first: LibraryName-version.cql
        String fileNameWithVersion = libraryName + "-" + version + ".cql";
        Source source = tryLoadResource(fileNameWithVersion);
        if (source != null) {
            return source;
        }

        // Try without version: LibraryName.cql
        String fileNameWithoutVersion = libraryName + ".cql";
        return tryLoadResource(fileNameWithoutVersion);
    }

    private Source tryLoadResource(String fileName) {
        try {
            ClassPathResource resource = new ClassPathResource(basePath + fileName);
            if (resource.exists()) {
                InputStream is = resource.getInputStream();
                return CoreKt.buffered(JvmCoreKt.asSource(is));
            }
        } catch (Exception e) {
            // Ignore and return null
        }
        return null;
    }
}
