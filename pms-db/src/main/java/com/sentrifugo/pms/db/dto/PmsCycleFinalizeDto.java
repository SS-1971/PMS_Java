package com.sentrifugo.pms.db.dto;

import java.util.UUID;

public record PmsCycleFinalizeDto(
        UUID ratingScaleId,
        boolean notifyManagers,
        boolean notifyEmployees,
        boolean notifyHod,
        boolean notifyHr) {
}
