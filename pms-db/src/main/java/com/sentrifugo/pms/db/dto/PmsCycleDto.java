package com.sentrifugo.pms.db.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entity-shaped view of a cycle. Enum columns are exposed as their wire strings
 * (e.g. {@code "draft"}); the {@code applicable_to} label and API envelope are
 * composed by the service from this.
 */
public record PmsCycleDto(
        UUID id,
        String cycleCode,
        String name,
        String description,
        String type,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        LocalDate publishedOn,
        LocalDateTime createdDate,
        PmsCycleApplicabilityDto applicability,
        @JsonProperty("finalize") PmsCycleFinalizeDto finalizeSettings,
        List<PmsCycleStageDto> stages,
        List<PmsCycleNotificationDto> notifications) {
}
