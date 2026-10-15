package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Screens 3.2 / 3.3: the employee's template KPIs with the manager's weights and targets. */
public record PmsEmployeeTargetsResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("goal_status") String goalStatus,
        @JsonProperty("template_id") UUID templateId,
        @JsonProperty("template_name") String templateName,
        @JsonProperty("kpis") List<Kpi> kpis,
        @JsonProperty("change_kpi_id") UUID changeKpiId,
        @JsonProperty("change_reason") String changeReason,
        @JsonProperty("change_proposed_target") BigDecimal changeProposedTarget,
        @JsonProperty("hod_remarks") String hodRemarks,
        /** Header-card context for screens 3.3 - 3.5; null where the caller is not the manager. */
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("emp_code") String empCode,
        @JsonProperty("role_name") String roleName,
        @JsonProperty("manager_name") String managerName,
        /** Day the manager sent the sheet to the employee; null before it is sent. */
        @JsonProperty("sent_on") java.time.LocalDate sentOn,
        /** Last day the employee can acknowledge, from the cycle's acknowledgement stage; null when not set. */
        @JsonProperty("acknowledge_by") java.time.LocalDate acknowledgeBy,
        /** The comment the employee left when acknowledging; null when none. */
        @JsonProperty("acknowledge_comment") String acknowledgeComment,
        /** A HOD-approved target revision still waiting for the employee to acknowledge (screen 4.5). */
        @JsonProperty("revision") Revision revision) {

    public record Kpi(
            @JsonProperty("kpi_id") UUID kpiId,
            @JsonProperty("kra_name") String kraName,
            @JsonProperty("kpi_name") String kpiName,
            @JsonProperty("unit") String unit,
            @JsonProperty("expected_outcome") String expectedOutcome,
            @JsonProperty("evidence_required") String evidenceRequired,
            @JsonProperty("weight") BigDecimal weight,
            @JsonProperty("target") BigDecimal target,
            /** The target the manager first assigned: never overwritten by a revision. */
            @JsonProperty("original_target") BigDecimal originalTarget,
            /** How many change requests the employee has made for this KPI on this sheet. */
            @JsonProperty("revision_count") int revisionCount,
            /** The manager's revised target (waiting to be acknowledged, or acknowledged); null when not revised. */
            @JsonProperty("revised_target") BigDecimal revisedTarget,
            /** The target before the revision; null when this KPI is not being revised. */
            @JsonProperty("previous_target") BigDecimal previousTarget,
            /** The actual the employee reported in Update Progress (view only for the manager); null if none. */
            @JsonProperty("employee_actual") BigDecimal employeeActual,
            /** The status the employee gave it: on_track, at_risk or off_track. */
            @JsonProperty("progress_status") String progressStatus,
            /** {@code common} or {@code individual}. */
            @JsonProperty("target_type") String targetType,
            /** Previous financial years, newest first, for the history panel on screen 3.3. */
            @JsonProperty("history") List<History> history) {
    }

    /** A revision waiting for the employee: the first revised KPI is named, every revised KPI has `revised_target`. */
    public record Revision(
            /** {@code hod_approved} or {@code manager_revised}. */
            @JsonProperty("source") String source,
            @JsonProperty("kpi_id") UUID kpiId,
            @JsonProperty("revised_target") BigDecimal revisedTarget,
            @JsonProperty("reason") String reason,
            @JsonProperty("approved_by") String approvedBy,
            @JsonProperty("approved_on") java.time.LocalDate approvedOn,
            @JsonProperty("acknowledge_by") java.time.LocalDate acknowledgeBy) {
    }

    public record History(
            @JsonProperty("financial_year") String financialYear,
            @JsonProperty("target") BigDecimal target,
            @JsonProperty("actual") BigDecimal actual) {
    }
}
