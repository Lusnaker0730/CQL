package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** PAT-257: one page of the measure list (same shape as the audit log page). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasurePage {
    private List<MeasureDefinition> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
