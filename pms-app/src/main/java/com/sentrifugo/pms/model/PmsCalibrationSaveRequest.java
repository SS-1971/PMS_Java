package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Screen 1.9 Save. */
public record PmsCalibrationSaveRequest(
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear,
        @JsonProperty("distribution") @NotEmpty @Valid List<Level> distribution,
        @JsonProperty("employees") @NotNull @Valid List<Employee> employees) {

    public record Level(
            @JsonProperty("rating") @Min(1) @Max(5) int rating,
            @JsonProperty("hr_percent") @Min(0) @Max(100) int hrPercent) {
    }

    public record Employee(
            @JsonProperty("employee_user_id") @NotBlank String employeeUserId,
            @JsonProperty("calibrated_rating") @Min(1) @Max(5) Integer calibratedRating,
            @JsonProperty("reason") @Size(max = 300) String reason) {
    }
}
