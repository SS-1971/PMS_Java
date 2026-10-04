package com.sentrifugo.pms.model.goaltemplate;

import com.sentrifugo.pms.db.dto.PmsGoalTemplateCompetencyDto;
import com.sentrifugo.pms.db.dto.PmsGoalTemplateKraDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API DTO family for {@code pms_goal_templates}; the KRA/KPI/competency entry shapes live in pms-db. */
public final class GoalTemplateDtos {

    private GoalTemplateDtos() {
    }

    public record Basic(
            @Min(2000) int financialYear,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description,
            @NotBlank String departmentId,
            @NotBlank String designationId,
            LocalDate effectiveFrom,
            @NotBlank String status) {
    }

    public record UpsertRequest(
            @NotNull @Valid Basic basic,
            @NotNull @Valid List<@Valid PmsGoalTemplateKraDto> kras,
            @NotNull @Valid List<@Valid PmsGoalTemplateCompetencyDto> competencies) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record Summary(UUID id, String name, int financialYear, String designationName, String departmentId,
                          String departmentName, String plantId, String plant, String status) {
    }
}
