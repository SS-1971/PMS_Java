package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Screen 3.12 Send to HOD for approval. Only changed targets are sent. */
public record PmsRevisionSubmitRequest(
        @JsonProperty("employee_user_id") @NotBlank String employeeUserId,
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear,
        @JsonProperty("rows") @NotNull @Size(min = 1) @Valid List<Row> rows,
        @JsonProperty("attachment_name") @Size(max = 255) String attachmentName) {

    public record Row(
            @JsonProperty("kpi_id") @NotNull UUID kpiId,
            @JsonProperty("revised_target") @NotNull BigDecimal revisedTarget,
            @JsonProperty("reason") @NotBlank @Size(max = 500) String reason) {
    }
}
