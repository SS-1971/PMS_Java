package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One decision the HOD already made (Approval History). */
public record PmsHodHistoryItemResponse(
        @JsonProperty("decided_on") LocalDate decidedOn,
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("manager_name") String managerName,
        @JsonProperty("request_type") String requestType,
        @JsonProperty("kpi_name") String kpiName,
        @JsonProperty("unit") String unit,
        @JsonProperty("from") BigDecimal from,
        @JsonProperty("to") BigDecimal to,
        @JsonProperty("more_changes") int moreChanges,
        /** {@code approved} or {@code returned}. */
        @JsonProperty("decision") String decision,
        @JsonProperty("remarks") String remarks) {
}
