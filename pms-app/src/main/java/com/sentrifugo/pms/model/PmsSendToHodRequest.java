package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Screen 3.6: acknowledged goal sheets the manager sends on to the HOD. */
public record PmsSendToHodRequest(
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear,
        @JsonProperty("employee_user_ids") @NotNull @Size(min = 1) List<@NotBlank String> employeeUserIds,
        /** The manager's note to the HOD, kept on the sheet trail. Optional. */
        @JsonProperty("remarks") @Size(max = 500, message = "Remarks can be at most 500 characters") String remarks) {
}
