package com.cqlplatform.model.fhir;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientSearchResult {
    private String id;
    private String name;
    private String birthDate;
    private String gender;
    private String identifier;
}
