package com.cqlplatform.model.fhir;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PatientMatchResult {
    private String patientId;
    private String patientName;
    private String birthDate;
    private String gender;
    private String identifier;
    private Long connectionId;
    private String connectionName;
    private int confidenceScore; // 0-100
    private String matchType; // exact, probable, possible
    private String matchDetails; // description of what matched
}
