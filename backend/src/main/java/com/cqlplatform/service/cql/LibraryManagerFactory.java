package com.cqlplatform.service.cql;

import com.cqlplatform.repository.CqlLibraryRepository;
import org.cqframework.cql.cql2elm.CqlCompilerOptions;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;

/**
 * Creates a pre-configured LibraryManager with database and classpath library providers.
 * Centralizes the setup that was previously duplicated in CqlTranslationService and CqlExecutionService.
 */
public final class LibraryManagerFactory {

    private LibraryManagerFactory() {}

    /**
     * Creates a LibraryManager with default compiler options
     * (EnableLocators, EnableAnnotations, EnableResultTypes).
     */
    public static LibraryManager create(CqlLibraryRepository libraryRepository) {
        return create(libraryRepository, defaultOptions());
    }

    /**
     * Creates a LibraryManager with the given compiler options.
     */
    public static LibraryManager create(CqlLibraryRepository libraryRepository, CqlCompilerOptions options) {
        // Ensure the context class loader is set correctly for ServiceLoader-based
        // model discovery (System model, FHIR model). Worker threads in
        // ThreadPoolExecutor / ForkJoinPool may lose the Spring Boot LaunchedURLClassLoader,
        // causing "Could not load model information for model System" errors.
        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        ClassLoader targetCl = LibraryManagerFactory.class.getClassLoader();
        if (originalCl != targetCl) {
            Thread.currentThread().setContextClassLoader(targetCl);
        }
        try {
            return doCreate(libraryRepository, options);
        } finally {
            if (originalCl != targetCl) {
                Thread.currentThread().setContextClassLoader(originalCl);
            }
        }
    }

    private static LibraryManager doCreate(CqlLibraryRepository libraryRepository, CqlCompilerOptions options) {
        ModelManager modelManager = new ModelManager();
        LibraryManager libraryManager = new LibraryManager(modelManager, options);

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

    /**
     * Builds CqlCompilerOptions from individual flags.
     */
    public static CqlCompilerOptions buildOptions(
            boolean enableLocators,
            boolean enableAnnotations,
            boolean enableResultTypes,
            boolean validateUnits) {
        CqlCompilerOptions options = new CqlCompilerOptions();
        if (enableLocators) options = options.withOptions(CqlCompilerOptions.Options.EnableLocators);
        if (enableAnnotations) options = options.withOptions(CqlCompilerOptions.Options.EnableAnnotations);
        if (enableResultTypes) options = options.withOptions(CqlCompilerOptions.Options.EnableResultTypes);
        options = options.withValidateUnits(validateUnits);
        return options;
    }

    private static CqlCompilerOptions defaultOptions() {
        return new CqlCompilerOptions(
                CqlCompilerOptions.Options.EnableLocators,
                CqlCompilerOptions.Options.EnableAnnotations,
                CqlCompilerOptions.Options.EnableResultTypes
        );
    }
}
