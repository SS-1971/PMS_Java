package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/** One row of screen 1.7 (HR Competency Rating). */
public record PmsHrQueueRow(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("emp_code") String empCode,
        @JsonProperty("department_id") String departmentId,
        @JsonProperty("functional_head") String functionalHead,
        @JsonProperty("hod_rated_on") LocalDate hodRatedOn,
        /** {@code pending}, {@code draft} or {@code rated}. */
        @JsonProperty("status") String status,
        @JsonProperty("sent_to_calibration") boolean sentToCalibration) {
}
