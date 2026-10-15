package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Save draft / submit body for screen 1.8. Ratings are 1-5; a draft may leave any of them null. */
public record PmsHrAppraisalSaveRequest(
        @JsonProperty("employee_user_id") @NotBlank String employeeUserId,
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear,
        @JsonProperty("competencies") @NotNull @Valid List<Line> competencies,
        @JsonProperty("remarks") @Size(max = 500) String remarks) {

    public record Line(
            @JsonProperty("id") @NotNull UUID id,
            @JsonProperty("rating") Integer rating,
            @JsonProperty("comment") @Size(max = 300) String comment) {
    }
}
