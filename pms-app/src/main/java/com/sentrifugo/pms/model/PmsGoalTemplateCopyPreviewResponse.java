package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response of {@code GET .../pms-goal-template/get/copy-preview}. */
public record PmsGoalTemplateCopyPreviewResponse(
        @JsonProperty("found") int found,
        @JsonProperty("already_in_target") int alreadyInTarget) {
}
