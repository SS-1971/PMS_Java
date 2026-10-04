package com.sentrifugo.pms.db.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Entity-shaped view of a goal template; department/designation names are resolved by the service. */
public record PmsGoalTemplateDto(
        UUID id,
        int financialYear,
        String name,
        String description,
        String departmentId,
        String designationId,
        LocalDate effectiveFrom,
        String status,
        List<PmsGoalTemplateKraDto> kras,
        List<PmsGoalTemplateCompetencyDto> competencies) {
}
