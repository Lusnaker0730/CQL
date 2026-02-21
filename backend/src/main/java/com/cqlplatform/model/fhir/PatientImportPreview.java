package com.cqlplatform.model.fhir;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientImportPreview {
    private String patientId;
    private String patientName;
    private Map<String, Integer> resourceCounts;
    private int totalResources;
}
