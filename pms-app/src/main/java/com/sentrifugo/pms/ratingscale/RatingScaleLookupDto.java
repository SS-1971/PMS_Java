package com.sentrifugo.pms.ratingscale;

import com.sentrifugo.db.config.RatingScale;

import java.util.List;
import java.util.UUID;

/** Trimmed shape {@code GET /pms/rating-scales} returns for the cycle wizard
 * (PMS Cycle contract #12) — same data as {@link RatingScaleConfigDto}, different field names. */
public record RatingScaleLookupDto(UUID id, String name, List<Level> levels) {

    public record Level(int value, String label, String description) {
    }

    public static RatingScaleLookupDto from(RatingScale scale) {
        List<Level> levels = scale.getLevels().stream()
                .map(l -> new Level(l.getRating(), l.getLabel(), l.getDefinition()))
                .toList();
        return new RatingScaleLookupDto(scale.getId(), scale.getName(), levels);
    }
}
