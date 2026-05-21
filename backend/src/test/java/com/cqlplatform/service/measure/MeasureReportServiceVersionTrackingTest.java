package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.MeasureReportRepository;
import com.cqlplatform.util.ContentHash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Locks measure version / hash provenance on saved reports (PAT-095, pre-launch
 * review #3). Tests the single-responsibility behavior: given a measure definition
 * row, the saved report carries {@code measureVersion / cqlHash / elmHash}
 * consistent with that row. Other saveReport concerns (populations normalization,
 * durations, fhirServerUrl) are left to existing integration coverage.
 */
@ExtendWith(MockitoExtension.class)
class MeasureReportServiceVersionTrackingTest {

    @Mock
    private MeasureReportRepository repository;
    @Mock
    private MeasureReportNormalizer normalizer;
    @Mock
    private MeasureDefinitionRepository measureDefinitionRepository;

    @InjectMocks
    private MeasureReportService service;

    private MeasureEvaluationResult minimalResult() {
        return MeasureEvaluationResult.builder()
                .measureId("m1")
                .measureName("M1")
                .status("complete")
                .reportType("summary")
                .periodStart(LocalDate.of(2024, 1, 1))
                .periodEnd(LocalDate.of(2024, 12, 31))
                .build();
    }

    private MeasureDefinitionEntity definition(String version, String cql, String elm) {
        MeasureDefinitionEntity d = new MeasureDefinitionEntity();
        d.setId(42L);
        d.setName("M1");
        d.setVersion(version);
        d.setCqlContent(cql);
        d.setElmJson(elm);
        return d;
    }

    @Test
    void whenDefinitionExists_savedReportCapturesVersionAndHashes() {
        String cql = "define \"Initial Population\": true";
        String elm = "{\"library\":{\"identifier\":{\"id\":\"M1\",\"version\":\"1.0.0\"}}}";
        MeasureDefinitionEntity def = definition("1.0.0", cql, elm);
        when(measureDefinitionRepository.findById(42L)).thenReturn(Optional.of(def));
        when(repository.save(any(MeasureReportEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.saveReport(minimalResult(), 42L, "http://fhir", "admin", 100L);

        ArgumentCaptor<MeasureReportEntity> captor = ArgumentCaptor.forClass(MeasureReportEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        MeasureReportEntity saved = captor.getValue();

        assertThat(saved.getMeasureVersion()).isEqualTo("1.0.0");
        assertThat(saved.getCqlHash()).isEqualTo(ContentHash.sha256Hex(cql));
        assertThat(saved.getElmHash()).isEqualTo(ContentHash.sha256Hex(elm));
    }

    @Test
    void whenCqlChanges_hashDiffers() {
        // Core provenance invariant: any CQL byte change → different hash → audit
        // can distinguish the two reports even at the same version string.
        String v1 = "define \"x\": true";
        String v2 = "define \"x\": false";
        assertThat(ContentHash.sha256Hex(v1)).isNotEqualTo(ContentHash.sha256Hex(v2));
    }

    @Test
    void whenDefinitionMissing_reportSavesWithNullProvenance() {
        // Definition could be deleted between evaluation and save. Report must still
        // save — the evaluation result itself is canonical — with null provenance
        // fields so the gap is visible rather than fabricated.
        when(measureDefinitionRepository.findById(999L)).thenReturn(Optional.empty());
        when(repository.save(any(MeasureReportEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.saveReport(minimalResult(), 999L, null, null, 0L);

        ArgumentCaptor<MeasureReportEntity> captor = ArgumentCaptor.forClass(MeasureReportEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        MeasureReportEntity saved = captor.getValue();

        assertThat(saved.getMeasureVersion()).isNull();
        assertThat(saved.getCqlHash()).isNull();
        assertThat(saved.getElmHash()).isNull();
    }

    @Test
    void whenMeasureDefinitionIdNull_noRepoCallMade() {
        // Measure-less report save (unusual — ad-hoc CQL execution) skips DB lookup
        // entirely rather than probing findById(null).
        when(repository.save(any(MeasureReportEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.saveReport(minimalResult(), null, null, null, 0L);

        org.mockito.Mockito.verify(measureDefinitionRepository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void whenCqlIsNullOnDefinition_cqlHashIsNull() {
        // Defensive: a definition row might exist with null cql_content (odd but
        // possible for stub entries). Hash must be null, not the SHA of empty string
        // (would falsely imply "cql was actually empty").
        MeasureDefinitionEntity def = definition("2.0.0", null, null);
        when(measureDefinitionRepository.findById(42L)).thenReturn(Optional.of(def));
        when(repository.save(any(MeasureReportEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.saveReport(minimalResult(), 42L, null, null, 0L);

        ArgumentCaptor<MeasureReportEntity> captor = ArgumentCaptor.forClass(MeasureReportEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        MeasureReportEntity saved = captor.getValue();

        assertThat(saved.getMeasureVersion()).isEqualTo("2.0.0");
        assertThat(saved.getCqlHash()).isNull();
        assertThat(saved.getElmHash()).isNull();
    }
}
