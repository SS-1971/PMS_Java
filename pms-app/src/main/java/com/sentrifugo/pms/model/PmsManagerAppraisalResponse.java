package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Screen 3.14: KRAs and competencies with the employee's self rating beside the manager's. */
public record PmsManagerAppraisalResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("emp_code") String empCode,
        @JsonProperty("period_label") String periodLabel,
        @JsonProperty("status") String status,
        @JsonProperty("kras") List<Kra> kras,
        @JsonProperty("competencies") List<Competency> competencies,
        @JsonProperty("hr_competencies") List<HrCompetency> hrCompetencies,
        @JsonProperty("recommended_rating") Integer recommendedRating,
        @JsonProperty("comments") String comments) {

    public record Kra(
            @JsonProperty("kpi_id") UUID kpiId,
            @JsonProperty("kra_name") String kraName,
            @JsonProperty("target") BigDecimal target,
            @JsonProperty("unit") String unit,
            @JsonProperty("actual") BigDecimal actual,
            @JsonProperty("self_rating") Integer selfRating,
            @JsonProperty("manager_rating") Integer managerRating,
            @JsonProperty("attachment_name") String attachmentName,
            @JsonProperty("comment") String comment) {
    }

    public record Competency(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("self_rating") Integer selfRating,
            @JsonProperty("manager_rating") Integer managerRating,
            @JsonProperty("comment") String comment) {
    }

    public record HrCompetency(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("self_rating") Integer selfRating) {
    }
}
