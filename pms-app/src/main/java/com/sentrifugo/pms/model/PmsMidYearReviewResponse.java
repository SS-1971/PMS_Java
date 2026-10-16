package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Screen 3.11: H1 progress against each target, and the manager's decision. */
public record PmsMidYearReviewResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("emp_code") String empCode,
        @JsonProperty("role") String role,
        @JsonProperty("manager") String manager,
        @JsonProperty("template") String template,
        @JsonProperty("status") String status,
        @JsonProperty("kras") List<Kra> kras,
        @JsonProperty("discussion_notes") String discussionNotes,
        @JsonProperty("revision_required") Boolean revisionRequired) {

    public record Kra(
            @JsonProperty("kpi_id") UUID kpiId,
            @JsonProperty("kra_name") String kraName,
            @JsonProperty("target") BigDecimal target,
            @JsonProperty("unit") String unit,
            @JsonProperty("h1_actual") BigDecimal h1Actual,
            @JsonProperty("rag") String rag) {
    }
}
