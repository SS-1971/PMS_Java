package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** One row of screen 3.13. */
public record PmsTeamAppraisalResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("role") String role,
        /** {@code submitted}, {@code not_submitted} or {@code not_applicable}. */
        @JsonProperty("self_status") String selfStatus,
        @JsonProperty("self_score") BigDecimal selfScore,
        /** {@code pending}, {@code draft}, {@code submitted_to_hod} or {@code waiting}. */
        @JsonProperty("manager_status") String managerStatus,
        @JsonProperty("manager_note") String managerNote) {
}
