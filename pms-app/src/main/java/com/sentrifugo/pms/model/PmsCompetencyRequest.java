package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Add / edit a competency (screen 2.9). {@code type} is {@code hr | manager}; when omitted it is taken from
 * the category (an "HR..." category is an HR competency). {@code status} is {@code active | inactive}.
 */
public record PmsCompetencyRequest(
        @JsonProperty("name") @NotBlank @Size(max = 200) String name,
        @JsonProperty("category") @NotBlank @Size(max = 100) String category,
        @JsonProperty("type") @Size(max = 20) String type,
        @JsonProperty("status") String status) {
}
