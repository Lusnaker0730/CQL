package com.cqlplatform.service.cql;

import com.cqlplatform.repository.CqlLibraryRepository;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;

/**
 * Creates a pre-configured LibraryManager with database and classpath library providers.
 * Centralizes the setup that was previously duplicated in CqlTranslationService and CqlExecutionService.
 */
public final class LibraryManagerFactory {

    private LibraryManagerFactory() {}

    /**
     * Creates a LibraryManager with:
     * 1. DatabaseLibrarySourceProvider (user libraries, takes precedence) — if repository is available
     * 2. ClasspathLibrarySourceProvider (FHIRHelpers and bundled libraries)
     */
    public static LibraryManager create(CqlLibraryRepository libraryRepository) {
        ModelManager modelManager = new ModelManager();
        LibraryManager libraryManager = new LibraryManager(modelManager);

        // Register database provider first so user libraries take precedence
        if (libraryRepository != null) {
            libraryManager.getLibrarySourceLoader()
                    .registerProvider(new DatabaseLibrarySourceProvider(libraryRepository));
        }

        // Register classpath provider to load FHIRHelpers from classpath resources
        libraryManager.getLibrarySourceLoader()
                .registerProvider(new ClasspathLibrarySourceProvider("cql"));

        return libraryManager;
    }
}
