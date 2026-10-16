package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** HOD approves or returns a goal sheet, or some of the KPIs of a target revision. */
public record PmsHodActionRequest(
        @JsonProperty("employee_user_id") @NotBlank String employeeUserId,
        @JsonProperty("financial_year") @NotBlank String financialYear,
        @JsonProperty("request_type") @NotBlank
        @Pattern(regexp = "goal_sheet|target_revision", message = "request_type must be goal_sheet or target_revision")
        String requestType,
        /** KPIs of a target revision to act on; empty means every KPI still waiting. Ignored for a goal sheet. */
        @JsonProperty("kpi_ids") List<UUID> kpiIds,
        @JsonProperty("remarks") @Size(max = 500, message = "Remarks can be at most 500 characters") String remarks) {
}
