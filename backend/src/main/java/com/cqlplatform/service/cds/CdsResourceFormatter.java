package com.cqlplatform.service.cds;

import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Formats FHIR R4 Resources into human-readable markdown strings for CDS card display.
 * Note: No HTML escaping — the frontend (React) auto-escapes text in JSX.
 */
@Component
public class CdsResourceFormatter {

    /**
     * Format a FHIR Resource into a detailed markdown string.
     * Includes resource type, id, and type-specific clinical fields.
     * Values are returned as-is; the frontend handles escaping.
     */
    public String formatDetail(Resource resource) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(resource.fhirType()).append("**");
        if (resource.hasIdElement()) {
            sb.append(" (").append(resource.getIdElement().getIdPart()).append(")");
        }

        if (resource instanceof Observation obs) {
            appendObservation(sb, obs);
        } else if (resource instanceof Condition cond) {
            appendCondition(sb, cond);
        } else if (resource instanceof MedicationRequest medReq) {
            appendMedicationRequest(sb, medReq);
        } else if (resource instanceof Procedure proc) {
            appendProcedure(sb, proc);
        } else if (resource instanceof AllergyIntolerance allergy) {
            appendAllergyIntolerance(sb, allergy);
        }

        return sb.toString();
    }

    /**
     * Format a FHIR Resource as a short reference: "Type/id".
     */
    public String formatReference(Resource resource) {
        return resource.fhirType() + (resource.hasIdElement() ? "/" + resource.getIdElement().getIdPart() : "");
    }

    /**
     * Format all codings in a CodeableConcept as a comma-separated display string.
     * Falls back to code if display is missing.
     */
    private String formatAllCodings(CodeableConcept cc) {
        if (cc.hasText()) {
            return cc.getText();
        }
        if (cc.hasCoding()) {
            return cc.getCoding().stream()
                    .map(c -> c.hasDisplay() ? c.getDisplay() : c.getCode())
                    .collect(Collectors.joining(", "));
        }
        return null;
    }

    private void appendObservation(StringBuilder sb, Observation obs) {
        if (obs.hasCode()) {
            String display = formatAllCodings(obs.getCode());
            if (display != null) {
                sb.append("\nCode: ").append(display);
            }
        }
        if (obs.hasValue()) {
            if (obs.getValue() instanceof Quantity q) {
                sb.append("\nValue: ").append(q.getValue()).append(" ").append(q.getUnit());
            } else if (obs.getValue() instanceof CodeableConcept cc) {
                String display = formatAllCodings(cc);
                sb.append("\nValue: ").append(display != null ? display : cc.toString());
            } else {
                sb.append("\nValue: ").append(obs.getValue().toString());
            }
        }
        if (obs.hasEffectiveDateTimeType()) {
            sb.append("\nDate: ").append(obs.getEffectiveDateTimeType().getValueAsString());
        }
    }

    private void appendCondition(StringBuilder sb, Condition cond) {
        if (cond.hasCode()) {
            String display = formatAllCodings(cond.getCode());
            if (display != null) {
                sb.append("\nCondition: ").append(display);
            }
        }
        if (cond.hasClinicalStatus()) {
            sb.append("\nStatus: ").append(cond.getClinicalStatus().getCodingFirstRep().getCode());
        }
    }

    private void appendMedicationRequest(StringBuilder sb, MedicationRequest medReq) {
        if (medReq.hasMedicationCodeableConcept()) {
            String display = formatAllCodings(medReq.getMedicationCodeableConcept());
            if (display != null) {
                sb.append("\nMedication: ").append(display);
            }
        }
        if (medReq.hasStatus()) {
            sb.append("\nStatus: ").append(medReq.getStatus().toCode());
        }
    }

    private void appendProcedure(StringBuilder sb, Procedure proc) {
        if (proc.hasCode()) {
            String display = formatAllCodings(proc.getCode());
            if (display != null) {
                sb.append("\nProcedure: ").append(display);
            }
        }
        if (proc.hasStatus()) {
            sb.append("\nStatus: ").append(proc.getStatus().toCode());
        }
    }

    private void appendAllergyIntolerance(StringBuilder sb, AllergyIntolerance allergy) {
        if (allergy.hasCode()) {
            String display = formatAllCodings(allergy.getCode());
            if (display != null) {
                sb.append("\nAllergy: ").append(display);
            }
        }
        if (allergy.hasClinicalStatus()) {
            sb.append("\nStatus: ").append(allergy.getClinicalStatus().getCodingFirstRep().getCode());
        }
    }
}
