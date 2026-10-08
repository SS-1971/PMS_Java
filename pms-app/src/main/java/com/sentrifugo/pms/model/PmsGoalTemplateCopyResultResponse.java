package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response of {@code POST .../pms-goal-template/copy}. */
public record PmsGoalTemplateCopyResultResponse(
        @JsonProperty("copied") int copied,
        @JsonProperty("skipped") int skipped) {
}
