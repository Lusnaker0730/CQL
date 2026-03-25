package com.cqlplatform.model.ehr;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchImportRequest {
    @NotEmpty(message = "Patient IDs list must not be empty")
    @Size(max = 500, message = "Maximum 500 patients per batch")
    private List<String> patientIds;

    private Long measureId;
}
