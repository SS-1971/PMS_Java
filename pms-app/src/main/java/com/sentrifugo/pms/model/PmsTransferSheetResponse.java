package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Screen 3.8: the goal sheet of an employee who changed department, role or manager mid-year. */
public record PmsTransferSheetResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("emp_code") String empCode,
        @JsonProperty("previous") Placement previous,
        @JsonProperty("current") Placement current,
        @JsonProperty("recorded_by") String recordedBy,
        @JsonProperty("recorded_on") LocalDate recordedOn,
        @JsonProperty("status") String status,
        @JsonProperty("kras") List<Kra> kras,
        @JsonProperty("reason") String reason,
        @JsonProperty("remarks") String remarks) {

    public record Placement(
            @JsonProperty("department") String department,
            @JsonProperty("manager") String manager,
            @JsonProperty("period_label") String periodLabel) {
    }

    public record Kra(
            @JsonProperty("id") UUID id,
            @JsonProperty("kra_name") String kraName,
            /** {@code template} or {@code manager}. */
            @JsonProperty("source") String source,
            @JsonProperty("source_label") String sourceLabel,
            @JsonProperty("period") int period,
            @JsonProperty("period_label") String periodLabel,
            @JsonProperty("weight") BigDecimal weight,
            @JsonProperty("target") BigDecimal target,
            @JsonProperty("unit") String unit,
            @JsonProperty("locked") boolean locked) {
    }
}
