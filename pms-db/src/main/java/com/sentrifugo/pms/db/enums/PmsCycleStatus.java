package com.sentrifugo.pms.db.enums;

import com.sentrifugo.common.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

/**
 * Persisted by constant name ({@code @Enumerated(EnumType.STRING)}); {@link #wire()}
 * is the lowercase form used by the REST API contract.
 */
public enum PmsCycleStatus {
    DRAFT("draft"),
    ACTIVE("active"),
    CLOSED("closed"),
    CANCELLED("cancelled");

    private final String wire;

    PmsCycleStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static PmsCycleStatus fromWire(String wire) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "status must be one of: draft, active, closed, cancelled", "VALIDATION_ERROR",
                        HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
