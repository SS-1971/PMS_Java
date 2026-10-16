package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Screen 1.9 "Approve &amp; Publish Ratings". */
public record PmsCalibrationPublishRequest(
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear) {
}
