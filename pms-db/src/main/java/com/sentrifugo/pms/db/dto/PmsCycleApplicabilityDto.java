package com.sentrifugo.pms.db.dto;

import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.List;

public record PmsCycleApplicabilityDto(
        List<String> plantIds,
        boolean allDepartments,
        List<String> departmentIds,
        List<String> employmentTypes,
        @Min(0) int minServiceMonths,
        LocalDate serviceAsOn,
        boolean excludeProbation,
        boolean excludeNoticePeriod) {

    public PmsCycleApplicabilityDto {
        plantIds = plantIds != null ? plantIds : List.of();
        departmentIds = departmentIds != null ? departmentIds : List.of();
        employmentTypes = employmentTypes != null ? employmentTypes : List.of();
    }
}
