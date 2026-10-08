package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST .../pms-goal-template/copy}: copies every template of {@code previous_year} into {@code target_year}. */
public record PmsGoalTemplateCopyRequest(
        @JsonProperty("previous_year") @NotBlank String previousYear,
        @JsonProperty("target_year") @NotBlank String targetYear) {
}
