package com.sentrifugo.pms.db.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a KRA; the organisation always comes from the security context, never from here. */
public record PmsKraUpsertDto(@NotBlank @Size(max = 100) String name) {
}
