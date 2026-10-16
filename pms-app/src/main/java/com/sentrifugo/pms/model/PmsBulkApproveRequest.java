package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/** Approve Selected on the Goal Approvals list. */
public record PmsBulkApproveRequest(
        @JsonProperty("financial_year") @NotBlank String financialYear,
        @JsonProperty("items") @NotEmpty @Valid List<Item> items) {

    public record Item(
            @JsonProperty("employee_user_id") @NotBlank String employeeUserId,
            @JsonProperty("request_type") @NotBlank
            @Pattern(regexp = "goal_sheet|target_revision", message = "request_type must be goal_sheet or target_revision")
            String requestType) {
    }
}
