package com.sentrifugo.pms.goaltemplate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTO family for {@code pms_goal_templates} — grouped in one file since every
 * shape here nests inside {@link GoalTemplateDto}; request and response reuse
 * the same {@code Kra}/{@code Kpi}/{@code Competency} shapes the contract does. */
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

    public record KpiEntry(
            @NotNull UUID kpiId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal weight,
            String targetType,
            @Size(max = 200) String expectedOutcome,
            @Size(max = 200) String evidenceRequired) {
    }

    public record KraEntry(@NotNull UUID kraId, @NotNull List<@Valid KpiEntry> kpis) {
    }

    public record CompetencyEntry(
            @NotNull UUID competencyId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal weight) {
    }

    public record UpsertRequest(
            @NotNull @Valid Basic basic,
            @NotNull @Valid List<@Valid KraEntry> kras,
            @NotNull @Valid List<@Valid CompetencyEntry> competencies) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record Summary(UUID id, String name, int financialYear, String designationName, String departmentId,
                          String departmentName, String plantId, String plant, String status) {
    }
}
