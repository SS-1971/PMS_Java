package com.sentrifugo.pms.db.enums;

import com.sentrifugo.common.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

/**
 * Persisted by constant name ({@code @Enumerated(EnumType.STRING)}); {@link #wire()}
 * is the lowercase form used by the REST API contract.
 */
public enum PmsCompetencyCategory {
    BEHAVIOURAL("behavioural"),
    TECHNICAL("technical"),
    LEADERSHIP("leadership"),
    FUNCTIONAL("functional");

    private final String wire;

    PmsCompetencyCategory(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static PmsCompetencyCategory fromWire(String wire) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "category must be one of: behavioural, technical, leadership, functional", "VALIDATION_ERROR",
                        HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
