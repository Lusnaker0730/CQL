package com.cqlplatform.service.terminology;

import com.cqlplatform.model.terminology.PlatformValueSet;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.ElmDependencies;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.ValueSetRef;
import com.cqlplatform.service.terminology.PlatformValueSetService.Resolved;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** PAT-260 — the pure pin rules: effective version, export overlay, provenance. */
class TerminologyPinsTest {

    private static final String A = "https://h/ValueSet/A";
    private static final String B = "https://h/ValueSet/B";
    private static final String X = "http://cts.nlm.nih.gov/fhir/ValueSet/X";

    private static ElmDependencies deps() {
        return new ElmDependencies(List.of(), List.of(
                new ValueSetRef("A", A, null), new ValueSetRef("B", B, "1.0.0"), new ValueSetRef("X", X, null)),
                List.of(), List.of());
    }

    @Test
    void withPins_fillsOnlyTheUnversionedReferences() {
        ElmDependencies pinned = TerminologyPins.withPins(deps(), Map.of(A, "3.0.0", B, "9.9.9"));

        assertThat(pinned.valueSets()).extracting(ValueSetRef::url, ValueSetRef::version)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(A, "3.0.0"),
                        org.assertj.core.groups.Tuple.tuple(B, "1.0.0"),      // the CQL version is kept
                        org.assertj.core.groups.Tuple.tuple(X, null));
        ElmDependencies untouched = deps();
        assertThat(TerminologyPins.withPins(untouched, Map.of())).isSameAs(untouched);   // no pins → the same object
    }

    @Test
    void provenance_recordsTheVersionEachReferenceResolved_orExternal() {
        PlatformValueSetService platform = mock(PlatformValueSetService.class);
        when(platform.resolve(eq(7L), eq(A), eq("3.0.0"))).thenReturn(Optional.of(new Resolved(
                PlatformValueSet.builder().url(A).version("3.0.0").build(), true)));
        when(platform.resolve(eq(7L), eq(B), eq("1.0.0"))).thenReturn(Optional.of(new Resolved(
                PlatformValueSet.builder().url(B).version("1.0.0").build(), true)));
        when(platform.resolve(eq(7L), eq(X), isNull())).thenReturn(Optional.empty());

        Map<String, String> used = TerminologyPins.provenance(TerminologyPins.valueSetsOf(deps()), Map.of(A, "3.0.0"), 7L, platform);

        assertThat(used).containsExactly(Map.entry(A, "3.0.0"), Map.entry(B, "1.0.0"), Map.entry(X, TerminologyPins.EXTERNAL));
    }

    @Test
    void effectiveVersion_cqlFirst_thenPin_thenNone() {
        assertThat(TerminologyPins.effectiveVersion(new TerminologyPins.ValueSetUse("B", B, "1.0.0"), Map.of(B, "2.0.0"))).isEqualTo("1.0.0");
        assertThat(TerminologyPins.effectiveVersion(new TerminologyPins.ValueSetUse("A", A, null), Map.of(A, "2.0.0"))).isEqualTo("2.0.0");
        assertThat(TerminologyPins.effectiveVersion(new TerminologyPins.ValueSetUse("A", A, null), Map.of())).isNull();
        assertThat(TerminologyPins.effectiveVersion(new TerminologyPins.ValueSetUse("A", A, null), null)).isNull();
    }
}
