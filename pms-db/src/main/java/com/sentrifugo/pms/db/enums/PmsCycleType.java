package com.sentrifugo.pms.db.enums;

import com.sentrifugo.common.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

/**
 * Persisted by constant name ({@code @Enumerated(EnumType.STRING)}); {@link #wire()}
 * is the lowercase form used by the REST API contract.
 */
public enum PmsCycleType {
    ANNUAL("annual"),
    MID_YEAR("mid_year"),
    CUSTOM("custom");

    private final String wire;

    PmsCycleType(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static PmsCycleType fromWire(String wire) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "type must be one of: annual, mid_year, custom", "VALIDATION_ERROR",
                        HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
