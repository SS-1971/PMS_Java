package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything that happened to one employee goals in a year, grouped by request (the Trail screen). */
public record PmsHodTrailResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("emp_code") String empCode,
        @JsonProperty("designation_name") String designationName,
        @JsonProperty("manager_name") String managerName,
        @JsonProperty("financial_year") String financialYear,
        /** For each KPI that moved, the value after every step. */
        @JsonProperty("moves") List<Move> moves,
        /** Newest request first. */
        @JsonProperty("requests") List<Request> requests) {

    public record Move(
            @JsonProperty("kpi_name") String kpiName,
            @JsonProperty("unit") String unit,
            @JsonProperty("steps") List<Step> steps) {
    }

    public record Step(
            @JsonProperty("date") LocalDate date,
            @JsonProperty("value") BigDecimal value,
            @JsonProperty("actor_role") String actorRole,
            @JsonProperty("label") String label) {
    }

    public record Request(
            @JsonProperty("request_no") int requestNo,
            @JsonProperty("request_type") String requestType,
            @JsonProperty("title") String title,
            @JsonProperty("subtitle") String subtitle,
            /** {@code approved}, {@code returned} or {@code waiting}. */
            @JsonProperty("status") String status,
            @JsonProperty("status_date") LocalDate statusDate,
            @JsonProperty("events") List<Event> events) {
    }

    public record Event(
            @JsonProperty("date") LocalDate date,
            @JsonProperty("actor_name") String actorName,
            @JsonProperty("actor_role") String actorRole,
            @JsonProperty("action") String action,
            @JsonProperty("summary") String summary) {
    }
}
