package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Screen 3.12: every KPI with its locked weight; only the targets can be revised. */
public record PmsRevisionRequestResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("emp_code") String empCode,
        @JsonProperty("role") String role,
        @JsonProperty("manager") String manager,
        @JsonProperty("template") String template,
        @JsonProperty("kras") List<Kra> kras,
        @JsonProperty("attachment_name") String attachmentName,
        @JsonProperty("status") String status) {

    public record Kra(
            @JsonProperty("kpi_id") UUID kpiId,
            @JsonProperty("kra_name") String kraName,
            @JsonProperty("weight") BigDecimal weight,
            @JsonProperty("current_target") BigDecimal currentTarget,
            @JsonProperty("unit") String unit,
            @JsonProperty("revised_target") BigDecimal revisedTarget,
            @JsonProperty("reason") String reason) {
    }
}
