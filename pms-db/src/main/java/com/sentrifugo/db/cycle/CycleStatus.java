package com.sentrifugo.db.cycle;

import com.sentrifugo.common.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum CycleStatus {
    DRAFT("draft"),
    ACTIVE("active"),
    CLOSED("closed"),
    CANCELLED("cancelled");

    private final String wire;

    CycleStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static CycleStatus fromWire(String wire) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "status must be one of: draft, active, closed, cancelled", "VALIDATION_ERROR",
                        HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
