package com.sentrifugo.pms.db.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * {@code notify} is illegal as a record component (clashes with {@code Object.notify()}),
 * so the Java name is {@code notifyEnabled} with the wire name pinned back to {@code notify}.
 */
public record PmsCycleStageDto(@NotBlank String stage, LocalDate startDate, LocalDate endDate,
                               @JsonProperty("notify") boolean notifyEnabled) {
}
