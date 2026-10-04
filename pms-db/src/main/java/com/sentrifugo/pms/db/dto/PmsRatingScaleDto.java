package com.sentrifugo.pms.db.dto;

import java.util.List;
import java.util.UUID;

public record PmsRatingScaleDto(UUID id, String name, boolean isDefault, boolean showDefinitionsToEmployees,
                                List<PmsRatingLevelDto> levels) {
}
