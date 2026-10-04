package com.sentrifugo.pms.db.enums;

import com.sentrifugo.common.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

/**
 * Persisted by constant name ({@code @Enumerated(EnumType.STRING)}); {@link #wire()}
 * is the lowercase form used by the REST API contract.
 */
public enum PmsTargetType {
    INDIVIDUAL("individual"),
    COMMON("common");

    private final String wire;

    PmsTargetType(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static PmsTargetType fromWire(String wire) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "target_type must be one of: individual, common", "VALIDATION_ERROR",
                        HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
