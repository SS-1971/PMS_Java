package com.sentrifugo.pms.db.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code isActive == null} means "leave unchanged" on update and "enabled" on create. */
public record PmsCompetencyUpsertDto(
        @NotBlank @Size(max = 120) String name,
        @NotBlank String category,
        Boolean isActive) {
}
