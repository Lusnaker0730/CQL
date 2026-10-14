package com.cqlplatform.service.terminology;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureTerminology;
import com.cqlplatform.model.terminology.PlatformValueSet;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.measure.CqfmLibraryBuilder;
import com.cqlplatform.service.measure.FhirCanonicalResolver;
import com.cqlplatform.service.measure.MeasureDefinitionService;
import com.cqlplatform.service.terminology.PlatformValueSetService.Resolved;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAT-260 — the terminology view of a measure (what its CQL references, how each resolves, which versions exist)
 * and the pin / unpin operations that go through the definition service's draft-only rule.
 */
@ExtendWith(MockitoExtension.class)
class MeasureTerminologyServiceTest {

    private static final long TENANT = 7L;
    private static final String PLATFORM = "https://hospital.example.tw/fhir/ValueSet/HbA1cOrders";
    private static final String EXTERNAL = "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.103.12.1001";
    private static final String ELM = "{\"library\":{\"valueSets\":{\"def\":["
            + "{\"name\":\"HbA1c Orders\",\"id\":\"" + PLATFORM + "\"},"
            + "{\"name\":\"Diabetes\",\"id\":\"" + EXTERNAL + "\",\"version\":\"20240501\"}]}}}";

    @Mock private MeasureDefinitionRepository repository;
    @Mock private MeasureDefinitionService definitionService;
    @Mock private CqlTranslationService translationService;
    @Mock private PlatformValueSetService platformValueSets;

    private MeasureTerminologyService service;
    private MeasureDefinitionEntity entity;

    @BeforeEach
    void setUp() {
        service = new MeasureTerminologyService(repository, definitionService,
                new CqfmLibraryBuilder(new FhirCanonicalResolver("https://hospital.example.tw/fhir", "")),
                translationService, platformValueSets);
        entity = MeasureDefinitionEntity.builder().id(10L).name("M").version("1.0.0").status("draft")
                .tenantId(TENANT).elmJson(ELM).build();
        lenient().when(definitionService.effectiveTenantId()).thenReturn(TENANT);
        lenient().when(repository.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(entity));
        lenient().when(platformValueSets.resolve(eq(TENANT), eq(EXTERNAL), any())).thenReturn(Optional.empty());
        lenient().when(definitionService.updateValueSetVersions(eq(10L), any(), any()))
                .thenAnswer(inv -> MeasureDefinition.builder().id(10L).valueSetVersions(inv.getArgument(1)).build());
    }

    private void platformHas(String resolvedVersion, String... allVersions) {
        PlatformValueSet vs = PlatformValueSet.builder().id(1L).url(PLATFORM).version(resolvedVersion).status("active").conceptCount(1).build();
        lenient().when(platformValueSets.resolve(eq(TENANT), eq(PLATFORM), any())).thenAnswer(inv -> {
            String asked = inv.getArgument(2);
            if (asked == null) return Optional.of(new Resolved(vs, false));
            for (String v : allVersions) if (v.equals(asked)) return Optional.of(new Resolved(
                    PlatformValueSet.builder().id(1L).url(PLATFORM).version(asked).status("active").conceptCount(1).build(), true));
            return Optional.empty();
        });
        lenient().when(platformValueSets.versions(1L)).thenReturn(java.util.Arrays.stream(allVersions)
                .map(v -> PlatformValueSet.builder().id(1L).url(PLATFORM).version(v).status(v.equals(resolvedVersion) ? "active" : "retired").conceptCount(1).build())
                .toList());
    }

    @Test
    void describe_tellsPlatformFromExternal_pinnedFromFloating_andListsTheVersions() {
        platformHas("2.0.0", "2.0.0", "1.0.0");

        MeasureTerminology view = service.describe(10L);

        assertThat(view.isPinnable()).isTrue();
        assertThat(view.getItems()).hasSize(2);
        MeasureTerminology.Item platform = view.getItems().get(0);
        assertThat(platform.getSource()).isEqualTo("platform");
        assertThat(platform.getResolvedVersion()).isEqualTo("2.0.0");
        assertThat(platform.isPinned()).isFalse();
        assertThat(platform.getVersions()).extracting(MeasureTerminology.Version::getVersion).containsExactly("2.0.0", "1.0.0");
        MeasureTerminology.Item external = view.getItems().get(1);
        assertThat(external.getSource()).isEqualTo("external");
        assertThat(external.getCqlVersion()).isEqualTo("20240501");
        assertThat(external.isPinned()).isTrue();          // the CQL names a version
        assertThat(external.getVersions()).isEmpty();
    }

    @Test
    void describe_translatesWhenNoElmIsStored() {
        entity.setElmJson(null);
        entity.setCqlContent("library M version '1.0.0'");
        when(translationService.translate(any())).thenReturn(
                com.cqlplatform.model.CqlTranslationResponse.builder().success(true).elmJson(ELM).build());
        platformHas("1.0.0", "1.0.0");

        assertThat(service.describe(10L).getItems()).hasSize(2);
    }

    @Test
    void pinAll_pinsEveryFloatingPlatformValueSetToWhatItResolvesNow() {
        platformHas("2.0.0", "2.0.0", "1.0.0");

        service.pin(10L, null, null, "alice");

        verify(definitionService).updateValueSetVersions(10L, Map.of(PLATFORM, "2.0.0"), "alice");
    }

    @Test
    void pinOne_toAnExistingVersion_orToTheCurrentOne_andRefusesUnknownOrExternalValueSets() {
        platformHas("2.0.0", "2.0.0", "1.0.0");

        service.pin(10L, PLATFORM, "1.0.0", "alice");
        verify(definitionService).updateValueSetVersions(10L, Map.of(PLATFORM, "1.0.0"), "alice");

        service.pin(10L, PLATFORM, "", "alice");
        verify(definitionService).updateValueSetVersions(10L, Map.of(PLATFORM, "2.0.0"), "alice");

        assertThatThrownBy(() -> service.pin(10L, PLATFORM, "9.9.9", "alice"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("no version 9.9.9");
        assertThatThrownBy(() -> service.pin(10L, EXTERNAL, null, "alice"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("only platform value sets");
        assertThatThrownBy(() -> service.pin(10L, "https://h/ValueSet/NotReferenced", null, "alice"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("does not reference");
    }

    @Test
    void unpin_removesOneOrAll() {
        platformHas("2.0.0", "2.0.0", "1.0.0");
        entity.setValueSetVersionMap(new LinkedHashMap<>(Map.of(PLATFORM, "1.0.0", "https://h/ValueSet/Other", "3.0.0")));

        service.unpin(10L, PLATFORM, "alice");
        verify(definitionService).updateValueSetVersions(10L, Map.of("https://h/ValueSet/Other", "3.0.0"), "alice");

        service.unpin(10L, null, "alice");
        verify(definitionService).updateValueSetVersions(10L, Map.of(), "alice");
        verify(repository, never()).save(any());   // the write goes through the definition service (lock + draft rule)
    }
}
