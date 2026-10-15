package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** One row of screen 3.10. */
public record PmsMidYearRowResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("role") String role,
        @JsonProperty("h1_achievement") BigDecimal h1Achievement,
        @JsonProperty("rag") String rag,
        @JsonProperty("status") String status) {
}
