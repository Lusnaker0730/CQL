package com.cqlplatform.validation;

import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.cds.CdsRequest;
import com.cqlplatform.model.cds.CdsServiceDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class HookContextRequirementsTest {

    // --- getRequiredFields ---

    @ParameterizedTest
    @ValueSource(strings = {"patient-view", "order-select", "order-sign",
            "encounter-start", "encounter-discharge", "appointment-book"})
    void getRequiredFields_allHookTypes_shouldReturnNonEmpty(String hook) {
        List<String> fields = HookContextRequirements.getRequiredFields(hook);
        assertThat(fields).isNotEmpty();
        assertThat(fields).contains("userId", "patientId");
    }

    @Test
    void getRequiredFields_unknownHook_shouldReturnEmpty() {
        assertThat(HookContextRequirements.getRequiredFields("unknown-hook")).isEmpty();
    }

    @Test
    void getRequiredFields_orderSelect_shouldIncludeSelectionsAndDraftOrders() {
        List<String> fields = HookContextRequirements.getRequiredFields("order-select");
        assertThat(fields).containsExactlyInAnyOrder("userId", "patientId", "selections", "draftOrders");
    }

    @Test
    void getRequiredFields_orderSign_shouldIncludeDraftOrders() {
        List<String> fields = HookContextRequirements.getRequiredFields("order-sign");
        assertThat(fields).containsExactlyInAnyOrder("userId", "patientId", "draftOrders");
    }

    @Test
    void getRequiredFields_encounterStart_shouldIncludeEncounterId() {
        List<String> fields = HookContextRequirements.getRequiredFields("encounter-start");
        assertThat(fields).containsExactlyInAnyOrder("userId", "patientId", "encounterId");
    }

    @Test
    void getRequiredFields_appointmentBook_shouldIncludeAppointments() {
        List<String> fields = HookContextRequirements.getRequiredFields("appointment-book");
        assertThat(fields).containsExactlyInAnyOrder("userId", "patientId", "appointments");
    }

    // --- validateContext: valid contexts ---

    @Test
    void validateContext_patientView_withAllRequired_shouldNotThrow() {
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setUserId("Practitioner/123");
        ctx.setPatientId("p1");

        assertThatCode(() -> HookContextRequirements.validateContext("patient-view", ctx))
                .doesNotThrowAnyException();
    }

    @Test
    void validateContext_orderSelect_withAllRequired_shouldNotThrow() {
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setUserId("Practitioner/123");
        ctx.setPatientId("p1");
        ctx.setSelections(List.of("MedicationRequest/123"));
        ctx.setDraftOrders(Map.of("resourceType", "Bundle"));

        assertThatCode(() -> HookContextRequirements.validateContext("order-select", ctx))
                .doesNotThrowAnyException();
    }

    @Test
    void validateContext_encounterStart_withAllRequired_shouldNotThrow() {
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setUserId("Practitioner/123");
        ctx.setPatientId("p1");
        ctx.setEncounterId("enc-1");

        assertThatCode(() -> HookContextRequirements.validateContext("encounter-start", ctx))
                .doesNotThrowAnyException();
    }

    @Test
    void validateContext_appointmentBook_withAllRequired_shouldNotThrow() {
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setUserId("Practitioner/123");
        ctx.setPatientId("p1");
        ctx.setAppointments(Map.of("resourceType", "Bundle"));

        assertThatCode(() -> HookContextRequirements.validateContext("appointment-book", ctx))
                .doesNotThrowAnyException();
    }

    // --- validateContext: missing fields ---

    @Test
    void validateContext_patientView_missingUserId_shouldThrow() {
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");

        assertThatThrownBy(() -> HookContextRequirements.validateContext("patient-view", ctx))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails()).hasSize(1);
                    assertThat(ve.getDetails().get(0)).contains("userId");
                });
    }

    @Test
    void validateContext_patientView_missingBothFields_shouldListBoth() {
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();

        assertThatThrownBy(() -> HookContextRequirements.validateContext("patient-view", ctx))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails()).hasSize(2);
                    assertThat(ve.getDetails()).anyMatch(d -> d.contains("userId"));
                    assertThat(ve.getDetails()).anyMatch(d -> d.contains("patientId"));
                });
    }

    @Test
    void validateContext_orderSelect_missingSelectionsAndDraftOrders_shouldThrow() {
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setUserId("Practitioner/123");
        ctx.setPatientId("p1");

        assertThatThrownBy(() -> HookContextRequirements.validateContext("order-select", ctx))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails()).hasSize(2);
                    assertThat(ve.getDetails()).anyMatch(d -> d.contains("selections"));
                    assertThat(ve.getDetails()).anyMatch(d -> d.contains("draftOrders"));
                });
    }

    @Test
    void validateContext_encounterStart_missingEncounterId_shouldThrow() {
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setUserId("Practitioner/123");
        ctx.setPatientId("p1");

        assertThatThrownBy(() -> HookContextRequirements.validateContext("encounter-start", ctx))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails()).hasSize(1);
                    assertThat(ve.getDetails().get(0)).contains("encounterId");
                });
    }

    @Test
    void validateContext_appointmentBook_missingAppointments_shouldThrow() {
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setUserId("Practitioner/123");
        ctx.setPatientId("p1");

        assertThatThrownBy(() -> HookContextRequirements.validateContext("appointment-book", ctx))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails()).hasSize(1);
                    assertThat(ve.getDetails().get(0)).contains("appointments");
                });
    }

    @Test
    void validateContext_nullContext_shouldThrow() {
        assertThatThrownBy(() -> HookContextRequirements.validateContext("patient-view", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Context is required");
    }

    @Test
    void validateContext_blankUserId_shouldCount_asMissing() {
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setUserId("   ");
        ctx.setPatientId("p1");

        assertThatThrownBy(() -> HookContextRequirements.validateContext("patient-view", ctx))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails()).anyMatch(d -> d.contains("userId"));
                });
    }

    @Test
    void validateContext_unknownHook_shouldNotThrow() {
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        assertThatCode(() -> HookContextRequirements.validateContext("unknown-hook", ctx))
                .doesNotThrowAnyException();
    }

    // --- getContextFieldDefinitions ---

    @Test
    void getContextFieldDefinitions_patientView_shouldReturnCorrectFields() {
        List<CdsServiceDefinition.ContextField> fields =
                HookContextRequirements.getContextFieldDefinitions("patient-view");

        assertThat(fields).hasSize(2);
        assertThat(fields).extracting("name").containsExactlyInAnyOrder("userId", "patientId");
        assertThat(fields).allMatch(CdsServiceDefinition.ContextField::isRequired);
        assertThat(fields).allMatch(f -> "string".equals(f.getType()));
    }

    @Test
    void getContextFieldDefinitions_orderSelect_shouldIncludeObjectFields() {
        List<CdsServiceDefinition.ContextField> fields =
                HookContextRequirements.getContextFieldDefinitions("order-select");

        assertThat(fields).hasSize(4);
        assertThat(fields).filteredOn(f -> "object".equals(f.getType()))
                .extracting("name")
                .containsExactlyInAnyOrder("selections", "draftOrders");
    }
}
