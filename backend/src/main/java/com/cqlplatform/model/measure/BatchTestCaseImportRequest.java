package com.cqlplatform.model.measure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchTestCaseImportRequest {
    @NotNull
    @Valid
    @Size(max = 200, message = "At most 200 test cases per batch")
    private List<TestCase> testCases;

    /** Number of days to shift all dates in patient bundles. 0 = no shifting. */
    @Min(-3650)
    @Max(3650)
    private int dateShiftDays;
}
