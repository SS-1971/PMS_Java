package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/** The transfer banner on screen 3.6. */
public record PmsTransferNoticeResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("joined_on") LocalDate joinedOn,
        @JsonProperty("from_department") String fromDepartment,
        @JsonProperty("previous_manager") String previousManager,
        @JsonProperty("recorded_by") String recordedBy) {
}
