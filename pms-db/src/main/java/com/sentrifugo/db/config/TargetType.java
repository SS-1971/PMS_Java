package com.sentrifugo.db.config;

import com.sentrifugo.common.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum TargetType {
    INDIVIDUAL("individual"),
    COMMON("common");

    private final String wire;

    TargetType(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static TargetType fromWire(String wire) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "target_type must be one of: individual, common", "VALIDATION_ERROR",
                        HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
