package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/** Screen 1.8: the HR competencies to rate now, and the manager competencies for reference. */
public record PmsHrAppraisalResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("emp_code") String empCode,
        @JsonProperty("role") String role,
        @JsonProperty("department_id") String departmentId,
        @JsonProperty("reporting_manager") String reportingManager,
        /** {@code pending}, {@code draft} or {@code rated}. */
        @JsonProperty("status") String status,
        /** True once the employee is with calibration; the ratings can no longer be changed. */
        @JsonProperty("locked") boolean locked,
        @JsonProperty("hr_competencies") List<HrCompetency> hrCompetencies,
        @JsonProperty("manager_competencies") List<ManagerCompetency> managerCompetencies,
        @JsonProperty("remarks") String remarks) {

    public record HrCompetency(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("self_rating") Integer selfRating,
            @JsonProperty("hr_rating") Integer hrRating,
            @JsonProperty("comment") String comment) {
    }

    public record ManagerCompetency(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("self_rating") Integer selfRating,
            @JsonProperty("manager_rating") Integer managerRating,
            @JsonProperty("hod_rating") Integer hodRating) {
    }
}
