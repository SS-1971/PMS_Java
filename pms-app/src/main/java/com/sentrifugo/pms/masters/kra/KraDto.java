package com.sentrifugo.pms.masters.kra;

import com.sentrifugo.db.config.Kra;

import java.util.UUID;

public record KraDto(UUID id, String name) {
    public static KraDto from(Kra kra) {
        return new KraDto(kra.getId(), kra.getName());
    }
}
