package com.cqlplatform.service.cds;

import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

/**
 * Formats FHIR R4 Resources into human-readable markdown strings for CDS card display.
 */
@Component
public class CdsResourceFormatter {

    /**
     * Format a FHIR Resource into a detailed markdown string.
     * Includes resource type, id, and type-specific clinical fields.
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

    private void appendObservation(StringBuilder sb, Observation obs) {
        if (obs.hasCode() && obs.getCode().hasText()) {
            sb.append("\nCode: ").append(obs.getCode().getText());
        } else if (obs.hasCode() && obs.getCode().hasCoding()) {
            sb.append("\nCode: ").append(obs.getCode().getCodingFirstRep().getDisplay());
        }
        if (obs.hasValue()) {
            if (obs.getValue() instanceof Quantity q) {
                sb.append("\nValue: ").append(q.getValue()).append(" ").append(q.getUnit());
            } else if (obs.getValue() instanceof CodeableConcept cc) {
                sb.append("\nValue: ").append(cc.hasText() ? cc.getText() : cc.getCodingFirstRep().getDisplay());
            } else {
                sb.append("\nValue: ").append(obs.getValue());
            }
        }
        if (obs.hasEffectiveDateTimeType()) {
            sb.append("\nDate: ").append(obs.getEffectiveDateTimeType().getValueAsString());
        }
    }

    private void appendCondition(StringBuilder sb, Condition cond) {
        if (cond.hasCode() && cond.getCode().hasText()) {
            sb.append("\nCondition: ").append(cond.getCode().getText());
        } else if (cond.hasCode() && cond.getCode().hasCoding()) {
            sb.append("\nCondition: ").append(cond.getCode().getCodingFirstRep().getDisplay());
        }
        if (cond.hasClinicalStatus()) {
            sb.append("\nStatus: ").append(cond.getClinicalStatus().getCodingFirstRep().getCode());
        }
    }

    private void appendMedicationRequest(StringBuilder sb, MedicationRequest medReq) {
        if (medReq.hasMedicationCodeableConcept()) {
            CodeableConcept med = medReq.getMedicationCodeableConcept();
            sb.append("\nMedication: ")
                    .append(med.hasText() ? med.getText() : med.getCodingFirstRep().getDisplay());
        }
        if (medReq.hasStatus()) {
            sb.append("\nStatus: ").append(medReq.getStatus().toCode());
        }
    }

    private void appendProcedure(StringBuilder sb, Procedure proc) {
        if (proc.hasCode() && proc.getCode().hasText()) {
            sb.append("\nProcedure: ").append(proc.getCode().getText());
        }
        if (proc.hasStatus()) {
            sb.append("\nStatus: ").append(proc.getStatus().toCode());
        }
    }

    private void appendAllergyIntolerance(StringBuilder sb, AllergyIntolerance allergy) {
        if (allergy.hasCode() && allergy.getCode().hasText()) {
            sb.append("\nAllergy: ").append(allergy.getCode().getText());
        }
        if (allergy.hasClinicalStatus()) {
            sb.append("\nStatus: ").append(allergy.getClinicalStatus().getCodingFirstRep().getCode());
        }
    }
}
