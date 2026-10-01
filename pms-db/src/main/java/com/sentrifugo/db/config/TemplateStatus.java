package com.sentrifugo.db.config;

import com.sentrifugo.common.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum TemplateStatus {
    DRAFT("draft"),
    ACTIVE("active"),
    INACTIVE("inactive");

    private final String wire;

    TemplateStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static TemplateStatus fromWire(String wire) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "status must be one of: draft, active, inactive", "VALIDATION_ERROR",
                        HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
