package com.sentrifugo.pms.masters.competency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompetencyUpsertRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank String category,
        Boolean isActive) {
}
