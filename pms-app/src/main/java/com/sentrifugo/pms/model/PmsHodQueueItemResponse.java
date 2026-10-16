package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of the HOD Goal Approvals list: a goal sheet or a mid-year target revision waiting for a decision. */
public record PmsHodQueueItemResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("emp_code") String empCode,
        @JsonProperty("designation_name") String designationName,
        @JsonProperty("manager_name") String managerName,
        /** {@code goal_sheet} or {@code target_revision}. */
        @JsonProperty("request_type") String requestType,
        @JsonProperty("goal_status") String goalStatus,
        /** False for a goal sheet whose targets did not move after the employee review. */
        @JsonProperty("changed") boolean changed,
        @JsonProperty("first_change") Change firstChange,
        @JsonProperty("more_changes") int moreChanges,
        @JsonProperty("asked_by_name") String askedByName,
        @JsonProperty("asked_by_role") String askedByRole,
        @JsonProperty("asked_on") LocalDate askedOn,
        /** Employee change requests plus HOD returns this sheet has been through. */
        @JsonProperty("revision_rounds") int revisionRounds) {

    public record Change(
            @JsonProperty("kpi_name") String kpiName,
            @JsonProperty("unit") String unit,
            @JsonProperty("from") BigDecimal from,
            @JsonProperty("to") BigDecimal to) {
    }
}
