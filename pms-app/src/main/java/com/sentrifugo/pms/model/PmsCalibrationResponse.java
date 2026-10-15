package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/** Screen 1.9: the rating distribution against HR's normalization targets, and each employee's rating. */
public record PmsCalibrationResponse(
        @JsonProperty("financial_year") String financialYear,
        /** True once the ratings were approved and published; the screen is then read-only. */
        @JsonProperty("published") boolean published,
        @JsonProperty("distribution") List<Level> distribution,
        @JsonProperty("employees") List<Employee> employees) {

    public record Level(
            @JsonProperty("rating") int rating,
            @JsonProperty("label") String label,
            @JsonProperty("current_percent") int currentPercent,
            @JsonProperty("guideline_percent") int guidelinePercent,
            @JsonProperty("hr_percent") int hrPercent) {
    }

    public record Employee(
            @JsonProperty("employee_user_id") String employeeUserId,
            @JsonProperty("employee_name") String employeeName,
            @JsonProperty("emp_code") String empCode,
            @JsonProperty("manager_rating") Integer managerRating,
            @JsonProperty("hod_rating") Integer hodRating,
            @JsonProperty("hr_competency_avg") BigDecimal hrCompetencyAvg,
            /** The rating before calibration: the HOD rating, else the manager's recommendation. */
            @JsonProperty("current_rating") Integer currentRating,
            @JsonProperty("calibrated_rating") Integer calibratedRating,
            @JsonProperty("reason") String reason) {
    }
}
