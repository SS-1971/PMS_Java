package com.sentrifugo.pms.masters.kra;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KraUpsertRequest(@NotBlank @Size(max = 100) String name) {
}
