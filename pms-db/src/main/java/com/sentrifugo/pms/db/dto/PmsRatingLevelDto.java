package com.sentrifugo.pms.db.dto;

import java.math.BigDecimal;

/** Full level shape used by the rating-scale config screen (2.10). */
public record PmsRatingLevelDto(int rating, String label, String definition, BigDecimal scoreMin,
                                BigDecimal scoreMax, String color) {
}
