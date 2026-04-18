package com.cqlplatform.service.cql;

import com.cqlplatform.entity.CqlLibraryEntity;
import com.cqlplatform.repository.CqlLibraryRepository;
import kotlinx.io.CoreKt;
import kotlinx.io.JvmCoreKt;
import kotlinx.io.Source;
import lombok.extern.slf4j.Slf4j;
import org.cqframework.cql.cql2elm.LibrarySourceProvider;
import org.hl7.elm.r1.VersionedIdentifier;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves CQL library source from the {@code cql_library} table.
 *
 * <h2>Defensive design (BUG-107 family)</h2>
 *
 * <p>The {@link DefaultLibrarySourceLoader} in cql-to-elm queries providers in
 * registration order and returns the first non-null source. When this provider is
 * registered alongside an {@code InMemoryLibrarySourceProvider} for a freshly-edited
 * library, the DB copy would silently win if its version number matches — returning
 * the stale stored CQL and running it instead of the editor text (BUG-107).
 *
 * <p>Two safeguards live in this class:
 * <ol>
 *   <li><b>Exclusion list.</b> Callers can register library identifiers that must
 *       NEVER be resolved from DB — typically the identifier of the CQL currently
 *       being translated. If the engine asks for an excluded identifier we return
 *       {@code null} and log a warning, forcing fallback to another provider
 *       (usually the in-memory provider holding the user's fresh text).</li>
 *   <li><b>Explicit logging.</b> Every successful DB lookup is logged at INFO level
 *       with the library id/version, so unexpected DB fallbacks are visible in
 *       operator telemetry instead of completing silently.</li>
 * </ol>
 */
@Slf4j
public class DatabaseLibrarySourceProvider implements LibrarySourceProvider {

    private final CqlLibraryRepository libraryRepository;

    /**
     * Identifiers whose DB resolution is suppressed. The InMemory provider (or any
     * fresher source) is expected to handle these. Keyed by id+version string pair to
     * tolerate the underlying VersionedIdentifier record's equality semantics.
     */
    private final ConcurrentHashMap<String, Boolean> excluded = new ConcurrentHashMap<>();

    public DatabaseLibrarySourceProvider(CqlLibraryRepository libraryRepository) {
        this.libraryRepository = libraryRepository;
    }

    /**
     * Prevent DB resolution for the given identifier. Idempotent.
     *
     * <p>Used by {@code CqlExecutionService.translateOnce} to protect the
     * freshly-translated library from being overwritten by its own stored copy on
     * subsequent resolution passes.
     */
    public void excludeIdentifier(VersionedIdentifier identifier) {
        if (identifier == null || identifier.getId() == null) return;
        excluded.put(key(identifier.getId(), identifier.getVersion()), Boolean.TRUE);
    }

    /** Remove a previously-set exclusion. Rarely needed — mainly for test isolation. */
    public void clearExclusion(VersionedIdentifier identifier) {
        if (identifier == null) return;
        excluded.remove(key(identifier.getId(), identifier.getVersion()));
    }

    /** Clear all exclusions. Used in tests. */
    public void clearExclusions() {
        excluded.clear();
    }

    @Override
    public Source getLibrarySource(VersionedIdentifier libraryIdentifier) {
        String name = libraryIdentifier.getId();
        String version = libraryIdentifier.getVersion();

        if (isExcluded(name, version)) {
            log.warn("DB fallback suppressed for excluded library identifier {}|{} "
                            + "(fresh source should be provided by in-memory provider)",
                    name, version);
            return null;
        }

        Optional<CqlLibraryEntity> entity;
        if (version != null && !version.isBlank()) {
            entity = libraryRepository.findByNameAndVersion(name, version);
        } else {
            // Resolve latest version
            List<CqlLibraryEntity> versions = libraryRepository.findByName(name);
            entity = versions.stream()
                    .max(Comparator.comparing(CqlLibraryEntity::getVersion, new SemanticVersionComparator()));
        }

        if (entity.isEmpty()) {
            return null;
        }

        CqlLibraryEntity e = entity.get();
        log.info("Resolved library {}|{} from DB (requested {}|{})",
                e.getName(), e.getVersion(), name, version);
        ByteArrayInputStream bais = new ByteArrayInputStream(
                e.getCqlContent().getBytes(StandardCharsets.UTF_8));
        return CoreKt.buffered(JvmCoreKt.asSource(bais));
    }

    private boolean isExcluded(String name, String version) {
        if (excluded.containsKey(key(name, version))) return true;
        // Also treat "no version specified" as excluded if ANY version of this name
        // is in the exclusion set — protects against ambiguous-version lookups falling
        // through to a DB latest-version resolution while a fresh translation is live.
        if (version == null) {
            return excluded.keySet().stream().anyMatch(k -> k.startsWith(name + "|"));
        }
        return false;
    }

    private static String key(String name, String version) {
        return Objects.toString(name, "") + "|" + Objects.toString(version, "");
    }
}
