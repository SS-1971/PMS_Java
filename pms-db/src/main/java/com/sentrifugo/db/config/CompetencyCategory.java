package com.sentrifugo.db.config;

import com.sentrifugo.common.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum CompetencyCategory {
    BEHAVIOURAL("behavioural"),
    TECHNICAL("technical"),
    LEADERSHIP("leadership"),
    FUNCTIONAL("functional");

    private final String wire;

    CompetencyCategory(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static CompetencyCategory fromWire(String wire) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "category must be one of: behavioural, technical, leadership, functional",
                        "VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
