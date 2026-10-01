package com.sentrifugo.db.cycle;

import com.sentrifugo.common.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum CycleType {
    ANNUAL("annual"),
    MID_YEAR("mid_year"),
    CUSTOM("custom");

    private final String wire;

    CycleType(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static CycleType fromWire(String wire) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "type must be one of: annual, mid_year, custom", "VALIDATION_ERROR",
                        HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
