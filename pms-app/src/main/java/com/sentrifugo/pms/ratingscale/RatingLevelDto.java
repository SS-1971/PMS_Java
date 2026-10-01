package com.sentrifugo.pms.ratingscale;

import java.math.BigDecimal;

/** Full level shape used by the config screen (2.10). */
public record RatingLevelDto(int rating, String label, String definition, BigDecimal scoreMin, BigDecimal scoreMax,
                             String color) {
}
