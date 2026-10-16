package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The HOD review screen: the KRAs/KPIs of one request, with who asked and why. */
public record PmsHodReviewResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("emp_code") String empCode,
        @JsonProperty("designation_name") String designationName,
        @JsonProperty("manager_name") String managerName,
        @JsonProperty("template_name") String templateName,
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("request_type") String requestType,
        @JsonProperty("goal_status") String goalStatus,
        @JsonProperty("asked_by_name") String askedByName,
        @JsonProperty("asked_by_role") String askedByRole,
        @JsonProperty("asked_on") LocalDate askedOn,
        /** The reason the employee gave when asking for a change; null when none. */
        @JsonProperty("employee_request") String employeeRequest,
        /** What the manager answered; null when none. */
        @JsonProperty("manager_reply") String managerReply,
        @JsonProperty("revision_rounds") int revisionRounds,
        @JsonProperty("total_weight") BigDecimal totalWeight,
        /** When the employee acknowledged the sheet; null when not yet. */
        @JsonProperty("acknowledged_on") LocalDate acknowledgedOn,
        /** When the manager sent the sheet to the HOD, and who sent it. */
        @JsonProperty("sent_for_approval_on") LocalDate sentForApprovalOn,
        @JsonProperty("sent_for_approval_by") String sentForApprovalBy,
        @JsonProperty("rows") List<Row> rows) {

    public record Row(
            @JsonProperty("kpi_id") UUID kpiId,
            @JsonProperty("kra_name") String kraName,
            @JsonProperty("kpi_name") String kpiName,
            @JsonProperty("weight") BigDecimal weight,
            @JsonProperty("unit") String unit,
            @JsonProperty("expected_outcome") String expectedOutcome,
            @JsonProperty("evidence_required") String evidenceRequired,
            /** What the employee asked for on this KPI (their latest change request); null when none. */
            @JsonProperty("employee_requested") BigDecimal employeeRequested,
            /** The target in force before this request (the original, or the currently approved one). */
            @JsonProperty("approved_now") BigDecimal approvedNow,
            @JsonProperty("requested") BigDecimal requested,
            @JsonProperty("change") BigDecimal change,
            @JsonProperty("change_percent") BigDecimal changePercent,
            @JsonProperty("reason") String reason,
            @JsonProperty("changed") boolean changed,
            /** {@code pending}, {@code approved} or {@code returned}. */
            @JsonProperty("decision") String decision) {
    }
}
