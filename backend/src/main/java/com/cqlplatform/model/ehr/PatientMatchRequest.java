package com.cqlplatform.model.ehr;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PatientMatchRequest {
    @Size(max = 20)
    private String nationalId;  // TW national ID

    @Size(max = 10)
    private String birthDate;   // yyyy-MM-dd

    @Size(max = 100)
    private String familyName;

    @Size(max = 100)
    private String givenName;

    @Size(max = 10)
    private String gender;      // male, female, other, unknown

    private List<Long> connectionIds; // null = search all connections
}
