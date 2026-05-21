package com.cqlplatform.service.cql;

import com.cqlplatform.repository.CqlLibraryRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.cqframework.cql.cql2elm.CqlCompilerOptions;
import org.cqframework.cql.cql2elm.LibraryBuilder;
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
     * Holder for a configured {@link LibraryManager} plus handles to its registered
     * providers so callers can adjust behavior after creation.
     *
     * <p>Most importantly, {@link #databaseProvider} is exposed so callers can exclude
     * the just-translated library's identifier to prevent the stale-DB-wins bug
     * (BUG-107 family). Null when no {@code CqlLibraryRepository} was supplied.
     */
    public static class LibraryContext {
        public final LibraryManager libraryManager;
        public final DatabaseLibrarySourceProvider databaseProvider; // nullable

        LibraryContext(LibraryManager libraryManager, DatabaseLibrarySourceProvider databaseProvider) {
            this.libraryManager = libraryManager;
            this.databaseProvider = databaseProvider;
        }
    }

    /**
     * Preferred factory — returns both the LibraryManager and the provider handles so
     * callers can apply per-translation safeguards.
     */
    public static LibraryContext createContext(CqlLibraryRepository libraryRepository) {
        return createContext(libraryRepository, defaultOptions());
    }

    public static LibraryContext createContext(CqlLibraryRepository libraryRepository, CqlCompilerOptions options) {
        ModelManager mm = sharedModelManager;
        if (mm == null) {
            // Fallback: if called before Spring init (e.g. in tests), create inline
            log.warn("sharedModelManager not yet initialized, creating inline (thread: {})",
                    Thread.currentThread().getName());
            mm = new ModelManager();
        }

        LibraryManager libraryManager = new LibraryManager(mm, options);

        DatabaseLibrarySourceProvider dbProvider = null;
        if (libraryRepository != null) {
            dbProvider = new DatabaseLibrarySourceProvider(libraryRepository);
            libraryManager.getLibrarySourceLoader().registerProvider(dbProvider);
        }

        libraryManager.getLibrarySourceLoader()
                .registerProvider(new ClasspathLibrarySourceProvider("cql"));

        return new LibraryContext(libraryManager, dbProvider);
    }

    /**
     * Creates a LibraryManager with default compiler options.
     *
     * <p>Prefer {@link #createContext(CqlLibraryRepository)} for new code — the Context
     * variant exposes the {@link DatabaseLibrarySourceProvider} handle needed to guard
     * against stale-DB-wins bugs.
     */
    public static LibraryManager create(CqlLibraryRepository libraryRepository) {
        return createContext(libraryRepository).libraryManager;
    }

    /**
     * Creates a LibraryManager with the given compiler options.
     *
     * <p>Kept for backward compatibility. Prefer {@link #createContext}.
     */
    public static LibraryManager create(CqlLibraryRepository libraryRepository, CqlCompilerOptions options) {
        return createContext(libraryRepository, options).libraryManager;
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
        options = options.withSignatureLevel(LibraryBuilder.SignatureLevel.Overloads);
        return options;
    }

    /**
     * SignatureLevel.Overloads embeds parameter-type signatures in ELM function refs for
     * any function with multiple overloads (e.g. {@code FHIRHelpers.ToString},
     * {@code FHIRHelpers.ToDateTime}, {@code FHIRHelpers.ToInterval}). Without this the
     * engine has to pick an overload by runtime argument type — which fails on null or
     * base-type arguments (same dispatch-ambiguity family as BUG-110 / BUG-112).
     * Narrower than {@code All} (doesn't bloat ELM for single-definition functions),
     * wider than {@code Differing} (catches every multi-overload case, not just those
     * the translator flags as "differing").
     */
    private static CqlCompilerOptions defaultOptions() {
        return new CqlCompilerOptions(
                CqlCompilerOptions.Options.EnableLocators,
                CqlCompilerOptions.Options.EnableAnnotations,
                CqlCompilerOptions.Options.EnableResultTypes
        ).withSignatureLevel(LibraryBuilder.SignatureLevel.Overloads);
    }
}
