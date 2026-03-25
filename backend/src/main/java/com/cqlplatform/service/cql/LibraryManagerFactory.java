package com.cqlplatform.service.cql;

import com.cqlplatform.repository.CqlLibraryRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.cqframework.cql.cql2elm.CqlCompilerOptions;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.springframework.stereotype.Component;

/**
 * Creates a pre-configured LibraryManager with database and classpath library providers.
 *
 * <p>A single {@link ModelManager} is created once during Spring initialization
 * (via {@code @PostConstruct}) on the main thread where the Spring Boot
 * {@code LaunchedURLClassLoader} is the context class loader. This avoids
 * {@code ServiceLoader} failures in worker threads (ForkJoinPool, ThreadPoolExecutor)
 * that have a different context class loader.</p>
 */
@Slf4j
@Component
public class LibraryManagerFactory {

    private static volatile ModelManager sharedModelManager;

    @PostConstruct
    void init() {
        // Create ModelManager on the Spring main thread where the correct class loader is set.
        // ModelManager uses ServiceLoader internally to discover ModelInfoProviders.
        log.info("Initializing shared CQL ModelManager on thread: {} with classloader: {}",
                Thread.currentThread().getName(),
                Thread.currentThread().getContextClassLoader());
        sharedModelManager = new ModelManager();
        log.info("CQL ModelManager initialized successfully");
    }

    /**
     * Creates a LibraryManager with default compiler options.
     */
    public static LibraryManager create(CqlLibraryRepository libraryRepository) {
        return create(libraryRepository, defaultOptions());
    }

    /**
     * Creates a LibraryManager with the given compiler options.
     */
    public static LibraryManager create(CqlLibraryRepository libraryRepository, CqlCompilerOptions options) {
        ModelManager mm = sharedModelManager;
        if (mm == null) {
            // Fallback: if called before Spring init (e.g. in tests), create inline
            log.warn("sharedModelManager not yet initialized, creating inline (thread: {})",
                    Thread.currentThread().getName());
            mm = new ModelManager();
        }

        LibraryManager libraryManager = new LibraryManager(mm, options);

        if (libraryRepository != null) {
            libraryManager.getLibrarySourceLoader()
                    .registerProvider(new DatabaseLibrarySourceProvider(libraryRepository));
        }

        libraryManager.getLibrarySourceLoader()
                .registerProvider(new ClasspathLibrarySourceProvider("cql"));

        return libraryManager;
    }

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
