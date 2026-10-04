package com.sentrifugo.pms.db.dto;

import java.util.UUID;

public record PmsCompetencyDto(UUID id, String name, String category, boolean isActive) {
}
