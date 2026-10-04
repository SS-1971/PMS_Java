package com.sentrifugo.pms.db.dto;

import java.util.List;
import java.util.UUID;

/** Trimmed shape the cycle wizard's rating-scale dropdown uses (PMS Cycle contract #12). */
public record PmsRatingScaleLookupDto(UUID id, String name, List<Level> levels) {

    public record Level(int value, String label, String description) {
    }
}
