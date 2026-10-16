package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Screen 3.11 Continue. A revision needs notes that justify it. */
public record PmsMidYearSubmitRequest(
        @JsonProperty("employee_user_id") @NotBlank String employeeUserId,
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear,
        @JsonProperty("discussion_notes") @Size(max = 1000) String discussionNotes,
        @JsonProperty("revision_required") @NotNull Boolean revisionRequired) {
}
