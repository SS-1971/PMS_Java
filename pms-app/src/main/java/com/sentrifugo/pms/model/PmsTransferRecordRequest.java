package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** HR records that an employee moved into a manager team, with the goals frozen as period 1. */
public record PmsTransferRecordRequest(
        @JsonProperty("employee_user_id") @NotBlank String employeeUserId,
        @JsonProperty("new_manager_user_id") @NotBlank String newManagerUserId,
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear,
        @JsonProperty("joined_on") @NotNull LocalDate joinedOn,
        @JsonProperty("from_department") @Size(max = 200) String fromDepartment,
        @JsonProperty("to_department") @Size(max = 200) String toDepartment,
        @JsonProperty("previous_manager_name") @Size(max = 200) String previousManagerName,
        @JsonProperty("previous_period_label") @Size(max = 100) String previousPeriodLabel,
        @JsonProperty("period_1_kras") @Valid List<Period1Kra> period1Kras) {

    public record Period1Kra(
            @JsonProperty("kra_name") @NotBlank @Size(max = 200) String kraName,
            @JsonProperty("weight") @NotNull BigDecimal weight,
            @JsonProperty("target") BigDecimal target,
            @JsonProperty("unit") @Size(max = 50) String unit) {
    }
}
