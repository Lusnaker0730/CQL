package com.cqlplatform.entity;

import com.cqlplatform.repository.MeasureReportRepository;
import com.cqlplatform.repository.PatientImportRepository;
import com.cqlplatform.repository.SandboxPresetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip integration test for PHI field encryption (PAT-100 Phase 1).
 *
 * <p>For each encrypted column, asserts that:
 * <ol>
 *   <li>The application-level getter returns the plaintext we wrote (JPA decrypts).
 *   <li>A raw JDBC SELECT against the same row returns ciphertext with the
 *       {@code ENC:} prefix — i.e. the data on disk is actually encrypted.
 * </ol>
 *
 * <p>Both directions must hold simultaneously. If only the app-level read passes,
 * we'd be shipping plaintext PHI to backup files undetected; if only the raw-DB
 * check passes, normal reads would break. Testing both together catches each
 * failure mode.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PhiEncryptionIntegrationTest {

    @Autowired
    private MeasureReportRepository measureReportRepository;
    @Autowired
    private PatientImportRepository patientImportRepository;
    @Autowired
    private SandboxPresetRepository sandboxPresetRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String PLAINTEXT_JSON =
            "{\"measureId\":\"test-measure\",\"groups\":[{\"populations\":[{\"patientId\":\"PID-123\"}]}]}";

    @Test
    void measureReport_resultJson_encryptedOnDisk() {
        MeasureReportEntity entity = MeasureReportEntity.builder()
                .measureName("Test Measure")
                .status("complete")
                .reportType("summary")
                .periodStart(LocalDate.of(2026, 1, 1))
                .periodEnd(LocalDate.of(2026, 6, 30))
                .scoringType("proportion")
                .resultJson(PLAINTEXT_JSON)
                .tenantId(1L)  // tenant_id NOT NULL since V63
                .build();

        MeasureReportEntity saved = measureReportRepository.save(entity);
        measureReportRepository.flush();

        // App-layer read — should be plaintext (converter decrypts).
        MeasureReportEntity reread = measureReportRepository.findById(saved.getId()).orElseThrow();
        assertThat(reread.getResultJson()).isEqualTo(PLAINTEXT_JSON);

        // Raw JDBC read — should show ciphertext with ENC: prefix.
        String rawValue = jdbcTemplate.queryForObject(
                "SELECT result_json FROM measure_report WHERE id = ?", String.class, saved.getId());
        assertThat(rawValue).startsWith("ENC:");
        assertThat(rawValue).doesNotContain("PID-123");  // patient ID must not leak in cipher text
        assertThat(rawValue).doesNotContain("test-measure");
    }

    @Test
    void measureReport_resultJson_legacyPlaintextStillReadable() {
        // Simulate a row that was written before PAT-100 deployed: raw plaintext
        // bypassing the converter. The converter's convertToEntityAttribute
        // fallback must still return it successfully.
        // tenant_id included: post-V63 even legacy rows carry a tenant (backfilled by V60/V63).
        jdbcTemplate.update(
                "INSERT INTO measure_report (measure_name, status, report_type, period_start, period_end, " +
                "scoring_type, result_json, created_at, tenant_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "Legacy", "complete", "summary",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30),
                "proportion",
                PLAINTEXT_JSON,
                java.time.LocalDateTime.now(),
                1L);

        // Read via JPA — converter sees no ENC: prefix and returns as-is.
        MeasureReportEntity legacy = measureReportRepository.findAll().stream()
                .filter(r -> "Legacy".equals(r.getMeasureName()))
                .findFirst().orElseThrow();
        assertThat(legacy.getResultJson()).isEqualTo(PLAINTEXT_JSON);
    }

    @Test
    void patientImport_allPhiFields_encryptedOnDisk() {
        PatientImportEntity entity = new PatientImportEntity();
        entity.setConnectionId(1L);
        entity.setPatientFhirId("Patient/pid-456");
        entity.setPatientIdentifier("MRN-789");
        entity.setPatientName("王小明");
        entity.setResourceCount(10);
        entity.setBundleJson("{\"resourceType\":\"Bundle\",\"entry\":[{\"name\":\"王小明\"}]}");
        entity.setImportedBy("test-user");

        PatientImportEntity saved = patientImportRepository.saveAndFlush(entity);

        PatientImportEntity reread = patientImportRepository.findById(saved.getId()).orElseThrow();
        assertThat(reread.getPatientFhirId()).isEqualTo("Patient/pid-456");
        assertThat(reread.getPatientIdentifier()).isEqualTo("MRN-789");
        assertThat(reread.getPatientName()).isEqualTo("王小明");
        assertThat(reread.getBundleJson()).contains("王小明");

        // Raw JDBC — each PHI column should be ciphertext.
        String rawRow = jdbcTemplate.queryForObject(
                "SELECT patient_fhir_id || '|' || patient_identifier || '|' || patient_name || '|' || bundle_json " +
                "FROM patient_import WHERE id = ?",
                String.class, saved.getId());
        // Four ENC: prefixes glued together with |.
        assertThat(rawRow.split("\\|")).allMatch(col -> col.startsWith("ENC:"));
        // PHI values must not appear verbatim in any encrypted column.
        assertThat(rawRow).doesNotContain("pid-456");
        assertThat(rawRow).doesNotContain("MRN-789");
        assertThat(rawRow).doesNotContain("王小明");
    }

    @Test
    void sandboxPreset_patientIdAndPrefetch_encryptedOnDisk() {
        SandboxPresetEntity entity = SandboxPresetEntity.builder()
                .name("Diabetes Preset")
                .ownerUsername("clinician-a")
                .serviceId("svc-1")
                .patientId("pid-diabetes-001")
                .prefetchJson("{\"Condition\":{\"resourceType\":\"Condition\",\"code\":\"E11\"}}")
                .shared(false)
                .build();

        SandboxPresetEntity saved = sandboxPresetRepository.saveAndFlush(entity);

        SandboxPresetEntity reread = sandboxPresetRepository.findById(saved.getId()).orElseThrow();
        assertThat(reread.getPatientId()).isEqualTo("pid-diabetes-001");
        assertThat(reread.getPrefetchJson()).contains("E11");

        String rawRow = jdbcTemplate.queryForObject(
                "SELECT patient_id || '|' || prefetch_json FROM sandbox_preset WHERE id = ?",
                String.class, saved.getId());
        assertThat(rawRow.split("\\|")).allMatch(col -> col.startsWith("ENC:"));
        assertThat(rawRow).doesNotContain("pid-diabetes-001");
        assertThat(rawRow).doesNotContain("E11");
    }
}
