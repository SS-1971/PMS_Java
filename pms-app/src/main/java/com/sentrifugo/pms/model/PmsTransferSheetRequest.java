package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Save draft / send body for screen 3.8. Only the editable period 2 KRAs are sent. */
public record PmsTransferSheetRequest(
        @JsonProperty("employee_user_id") @NotBlank String employeeUserId,
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear,
        @JsonProperty("kras") @NotNull @Valid List<Kra> kras,
        @JsonProperty("reason") @Size(max = 100) String reason,
        @JsonProperty("remarks") String remarks) {

    public record Kra(
            @JsonProperty("kra_name") @NotBlank @Size(max = 200) String kraName,
            @JsonProperty("weight") @NotNull BigDecimal weight,
            @JsonProperty("target") BigDecimal target,
            @JsonProperty("unit") @Size(max = 50) String unit,
            /** {@code template} when the row came from a role template, else {@code manager}. */
            @JsonProperty("source") String source,
            @JsonProperty("source_label") @Size(max = 100) String sourceLabel) {
    }
}
