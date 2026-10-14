package com.cqlplatform.service.terminology;

import com.cqlplatform.service.measure.CqfmLibraryBuilder.ElmDependencies;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.ValueSetRef;
import org.hl7.elm.r1.Library;
import org.hl7.elm.r1.ValueSetDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PAT-260 — the pure rules of terminology pinning.
 *
 * <p>A measure's pins are {@code value set URL → version}. The engine uses them for references whose CQL names no
 * version ({@link PlatformTerminologyProvider}); the CQFM export writes them as {@code url|version}; a report records
 * what was actually resolved ({@link #provenance}). An explicit CQL {@code version '…'} always wins over a pin.
 */
public final class TerminologyPins {

    /** Marker for a value set the platform does not own (IG / VSAC / remote server resolved it, or nobody did). */
    public static final String EXTERNAL = "external";

    private TerminologyPins() {}

    /** A value set the translated library references: its CQL name, URL and the version the CQL names (or null). */
    public record ValueSetUse(String name, String url, String cqlVersion) {}

    public static List<ValueSetUse> valueSetsOf(Library library) {
        List<ValueSetUse> out = new ArrayList<>();
        if (library == null || library.getValueSets() == null) return out;
        for (ValueSetDef def : library.getValueSets().getDef()) {
            if (def.getId() == null || def.getId().isBlank()) continue;
            out.add(new ValueSetUse(def.getName(), def.getId(), blankToNull(def.getVersion())));
        }
        return out;
    }

    public static List<ValueSetUse> valueSetsOf(ElmDependencies deps) {
        List<ValueSetUse> out = new ArrayList<>();
        if (deps == null || deps.valueSets() == null) return out;
        for (ValueSetRef ref : deps.valueSets()) out.add(new ValueSetUse(ref.name(), ref.url(), blankToNull(ref.version())));
        return out;
    }

    /** The version a reference evaluates with: the CQL's own, else the pin, else null (newest active). */
    public static String effectiveVersion(ValueSetUse use, Map<String, String> pins) {
        if (use.cqlVersion() != null) return use.cqlVersion();
        return pins == null ? null : blankToNull(pins.get(use.url()));
    }

    /**
     * url → version this evaluation resolved: the effective version when the platform owns the value set (or the
     * newest active version it fell back to), {@link #EXTERNAL} otherwise. Empty when nothing is referenced.
     */
    public static Map<String, String> provenance(List<ValueSetUse> uses, Map<String, String> pins, Long tenantId,
                                                 PlatformValueSetService platform) {
        Map<String, String> out = new LinkedHashMap<>();
        for (ValueSetUse use : uses) {
            String version = effectiveVersion(use, pins);
            Optional<PlatformValueSetService.Resolved> resolved = platform != null && tenantId != null
                    ? platform.resolve(tenantId, use.url(), version) : Optional.empty();
            out.put(use.url(), resolved.map(r -> r.valueSet().getVersion()).orElse(EXTERNAL));
        }
        return out;
    }

    /** The dependencies with every unversioned value set reference given its pinned version (export writes url|version). */
    public static ElmDependencies withPins(ElmDependencies deps, Map<String, String> pins) {
        if (deps == null || pins == null || pins.isEmpty() || deps.valueSets() == null) return deps;
        List<ValueSetRef> valueSets = new ArrayList<>();
        for (ValueSetRef ref : deps.valueSets()) {
            String version = blankToNull(ref.version()) != null ? ref.version() : blankToNull(pins.get(ref.url()));
            valueSets.add(new ValueSetRef(ref.name(), ref.url(), version));
        }
        return new ElmDependencies(deps.includes(), valueSets, deps.codeSystems(), deps.parameters());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
