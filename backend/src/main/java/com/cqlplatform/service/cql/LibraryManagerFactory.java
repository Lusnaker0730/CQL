package com.cqlplatform.service.cql;

import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class LibraryManagerFactory {

    private static volatile ModelManager sharedModelManager;

    /**
     * BUG-152 (found by the PAT-266 concurrency run): cql-to-elm's {@link ModelManager} caches models in
     * plain collections and detects recursion with a "loading" set, so two threads loading the same
     * model for the first time make each other fail with "Circular model reference FHIR" — every
     * translation that started together right after boot failed that way. First loads go through
     * this lock; afterwards the cache is only read.
     */
    private static final Object MODEL_LOAD_LOCK = new Object();

    /** {@code using FHIR version '4.0.1'} — the models a library declares, pre-loaded under the lock. */
    private static final java.util.regex.Pattern USING_DECLARATION = java.util.regex.Pattern.compile(
            "(?m)^\\s*using\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+version\\s+'([^']*)')?");

    /**
     * Resolves the default tenant's id for {@link DatabaseLibrarySourceProvider}'s
     * null-TenantContext fallback (PAT-193 / #697). Static because the factory methods
     * are static; wired from the Spring-managed instance in {@link #init()}. Memoized —
     * the id is immutable after the V57 migration, so one successful lookup suffices.
     * Null until Spring init (tests / non-Spring callers keep the legacy behaviour).
     */
    private static volatile java.util.function.Supplier<Long> defaultTenantIdResolver;

    private final TenantRepository tenantRepository;

    @PostConstruct
    void init() {
        // Create ModelManager on the Spring main thread where the correct class loader is set.
        // ModelManager uses ServiceLoader internally to discover ModelInfoProviders.
        log.info("Initializing shared CQL ModelManager on thread: {} with classloader: {}",
                Thread.currentThread().getName(),
                Thread.currentThread().getContextClassLoader());
        sharedModelManager = new ModelManager();
        log.info("CQL ModelManager initialized successfully");
        warmUp(sharedModelManager);

        final TenantRepository repo = tenantRepository;
        defaultTenantIdResolver = new java.util.function.Supplier<>() {
            private volatile Long cached;

            @Override
            public Long get() {
                Long id = cached;
                if (id != null) {
                    return id;
                }
                try {
                    id = repo.findByCode("default")
                            .map(com.cqlplatform.entity.TenantEntity::getId)
                            .orElse(null);
                } catch (RuntimeException e) {
                    // Degrade gracefully — the provider falls back to the legacy unscoped
                    // lookup and WARNs; never fail a translation over tenant resolution.
                    log.warn("Default tenant lookup failed: {}", e.getMessage());
                    return null;
                }
                cached = id;
                return id;
            }
        };
    }

    /** BUG-152: the models the platform uses, loaded before the first request can race for them. */
    private static void warmUp(ModelManager modelManager) {
        synchronized (MODEL_LOAD_LOCK) {
            for (String[] model : new String[][] {{"System", null}, {"FHIR", "4.0.1"}}) {
                try {
                    resolve(modelManager, model[0], model[1]);
                } catch (RuntimeException e) {
                    log.warn("CQL model {} {} could not be pre-loaded: {}", model[0], model[1], e.getMessage());
                }
            }
        }
        log.info("CQL models pre-loaded (System, FHIR 4.0.1)");
    }

    /**
     * BUG-152: loads the models {@code cql} declares with {@code using} into the shared
     * {@link ModelManager} under the load lock, so a concurrent first translation never trips the
     * manager's recursion guard. Call before {@code CqlTranslator.fromText}. A model the manager
     * cannot load is left for the translator to report; nothing is thrown here.
     */
    public static void ensureModelsLoaded(String cql) {
        ModelManager modelManager = sharedModelManager;
        if (modelManager == null || cql == null) {
            return;
        }
        java.util.regex.Matcher m = USING_DECLARATION.matcher(cql);
        while (m.find()) {
            synchronized (MODEL_LOAD_LOCK) {
                try {
                    resolve(modelManager, m.group(1), m.group(2));
                } catch (RuntimeException e) {
                    log.debug("CQL model {} {} not pre-loadable ({}); the translator will report it",
                            m.group(1), m.group(2), e.getMessage());
                }
            }
        }
    }

    private static void resolve(ModelManager modelManager, String id, String version) {
        if (version == null || version.isBlank()) {
            modelManager.resolveModel(id);
        } else {
            modelManager.resolveModel(id, version);
        }
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
            dbProvider = new DatabaseLibrarySourceProvider(libraryRepository, defaultTenantIdResolver);
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
