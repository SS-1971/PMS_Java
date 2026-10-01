package com.sentrifugo.pms.ratingscale;

import com.sentrifugo.db.config.RatingScale;

import java.util.List;
import java.util.UUID;

public record RatingScaleConfigDto(UUID id, String name, boolean isDefault, boolean showDefinitionsToEmployees,
                                   List<RatingLevelDto> levels) {
    public static RatingScaleConfigDto from(RatingScale scale) {
        List<RatingLevelDto> levels = scale.getLevels().stream()
                .map(l -> new RatingLevelDto(l.getRating(), l.getLabel(), l.getDefinition(), l.getScoreMin(),
                        l.getScoreMax(), l.getColor()))
                .toList();
        return new RatingScaleConfigDto(scale.getId(), scale.getName(), scale.isDefault(),
                scale.isShowDefinitionsToEmployees(), levels);
    }
}
