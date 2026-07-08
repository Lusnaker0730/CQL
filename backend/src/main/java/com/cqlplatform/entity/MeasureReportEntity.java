package com.cqlplatform.entity;

import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.security.EncryptionConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.LongAdder;

@Entity
@Table(name = "measure_report")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class MeasureReportEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Process-wide counter of failed result_json deserializations. Exposed via
     * {@link #getDeserializationFailureCount()} for tests / health endpoints.
     *
     * <p>A non-zero value means at least one historical report is no longer loadable
     * — usually caused by a {@link MeasureEvaluationResult} schema change without a
     * corresponding data migration. Before this safeguard, deserialization failures
     * silently produced null {@code evaluationResult} and the dashboard rendered
     * empty groups. Now each failure WARN-logs with the report id so operators see
     * it in telemetry.
     */
    private static final LongAdder DESERIALIZATION_FAILURES = new LongAdder();

    public static long getDeserializationFailureCount() {
        return DESERIALIZATION_FAILURES.sum();
    }

    /** Test-only: reset the counter between runs. Not for production use. */
    public static void resetDeserializationFailureCount() {
        DESERIALIZATION_FAILURES.reset();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_definition_id")
    private Long measureDefinitionId;

    /**
     * Snapshot of {@code MeasureDefinition.version} at evaluation time. Lets an auditor
     * answer "which version of measure X produced this result" without depending on
     * the (mutable) definition row — measures can be edited or deleted; the report's
     * self-contained provenance survives. Null for legacy rows created before PAT-095.
     */
    @Column(name = "measure_version", length = 50)
    private String measureVersion;

    /**
     * SHA-256 hex digest of the CQL source string that was executed. Two identical CQL
     * strings produce the same hash; any byte-level change (including whitespace) bumps
     * it. Pairs with {@code measureVersion} — the version is human-readable, the hash
     * is forensic-grade. Null for legacy rows created before PAT-095.
     */
    @Column(name = "cql_hash", length = 64)
    private String cqlHash;

    /**
     * SHA-256 hex digest of the translated ELM JSON. More stable than cql_hash across
     * cosmetic CQL changes (comment updates, whitespace) because the translator
     * normalizes to ELM semantics — if two cql_hashes differ but elm_hashes match, the
     * measure logic is semantically equivalent. Null for legacy rows.
     */
    @Column(name = "elm_hash", length = 64)
    private String elmHash;

    @Column(name = "measure_name", nullable = false, length = 200)
    private String measureName;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "report_type", length = 30)
    private String reportType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "scoring_type", length = 30)
    private String scoringType;

    @Column(name = "measure_score")
    private Double measureScore;

    @Column(name = "total_patients")
    private Integer totalPatients;

    /**
     * Full serialized {@link MeasureEvaluationResult} JSON. Contains subject IDs,
     * per-population membership, and computed scores — all PHI. Encrypted at rest
     * via {@link EncryptionConverter}; new writes get `ENC:` prefix, legacy
     * plaintext rows remain readable via the converter's fallback path.
     */
    @Column(name = "result_json", columnDefinition = "TEXT", nullable = false)
    @Convert(converter = EncryptionConverter.class)
    private String resultJson;

    @Transient
    private MeasureEvaluationResult evaluationResult;

    @Column(name = "fhir_server_url", length = 500)
    private String fhirServerUrl;

    @Column(name = "evaluated_by", length = 100)
    private String evaluatedBy;

    @Column(name = "evaluation_duration_ms")
    private Long evaluationDurationMs;

    @Column(name = "department", length = 100)
    private String department;

    /**
     * Tenant (clinic) this report belongs to — the isolation boundary (Phase 2).
     * Assigned server-side at save; existing rows were backfilled to the default tenant.
     * Nullable for now (foundation); the enforcement PR scopes reads and makes it NOT NULL.
     */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PostLoad
    protected void onLoad() {
        if (resultJson != null && !resultJson.isBlank()) {
            try {
                evaluationResult = MAPPER.readValue(resultJson, MeasureEvaluationResult.class);
            } catch (JsonProcessingException e) {
                // Previously the failure was swallowed to null — the dashboard silently rendered
                // empty groups when the stored JSON couldn't be deserialized (usually after a
                // MeasureEvaluationResult schema change without data migration). Surface it now.
                DESERIALIZATION_FAILURES.increment();
                log.warn("Failed to deserialize measure_report.result_json for report id={}: {} ({}). "
                                + "The record is still accessible via column fields (status, measure_score, "
                                + "total_patients, period) but getEvaluationResult() will return null.",
                        id, e.getClass().getSimpleName(), e.getOriginalMessage());
                evaluationResult = null;
            }
        }
    }
}
