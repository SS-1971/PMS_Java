package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Per-sheet outcome of a bulk approve: nothing is skipped silently. */
public record PmsBulkApproveResponse(
        @JsonProperty("approved") int approved,
        @JsonProperty("failed") int failed,
        @JsonProperty("results") List<Result> results) {

    public record Result(
            @JsonProperty("employee_user_id") String employeeUserId,
            @JsonProperty("employee_name") String employeeName,
            @JsonProperty("request_type") String requestType,
            @JsonProperty("success") boolean success,
            @JsonProperty("message") String message) {
    }
}
