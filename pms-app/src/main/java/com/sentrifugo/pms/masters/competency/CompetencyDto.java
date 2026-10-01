package com.sentrifugo.pms.masters.competency;

import com.sentrifugo.db.config.Competency;

import java.util.UUID;

public record CompetencyDto(UUID id, String name, String category, boolean isActive) {
    public static CompetencyDto from(Competency competency) {
        return new CompetencyDto(competency.getId(), competency.getName(), competency.getCategory().wire(),
                competency.isActive());
    }
}
