package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Screen 1.7 "Send Rated to Calibration". */
public record PmsSendToCalibrationRequest(
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear,
        @JsonProperty("employee_user_ids") @NotEmpty List<String> employeeUserIds) {
}
