package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** Where the employee's self appraisal stands after a save or submit. */
public record PmsSelfAppraisalResponse(
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("status") String status,
        @JsonProperty("self_score") BigDecimal selfScore) {
}
