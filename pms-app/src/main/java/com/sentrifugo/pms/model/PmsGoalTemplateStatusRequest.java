package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** Body of {@code PUT .../pms-goal-template/update/goal-template/{id}/status}: {@code draft}, {@code active} or {@code inactive}. */
public record PmsGoalTemplateStatusRequest(
        @JsonProperty("status") @NotBlank String status) {
}
