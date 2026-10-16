package com.sentrifugo.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One employee as IAM's {@code direct_reports} RPC queue returns it (see
 * {@code Sentrifugo-IAM-Admin-BE/src/rpc/direct_reports.py}). Field names map to
 * IAM's snake_case reply keys ({@code user_id}, {@code emp_code}, ...) through
 * the app's global {@code spring.jackson.property-naming-strategy=SNAKE_CASE},
 * the same as every other DTO in this codebase -- no per-field annotations
 * needed now that this no longer mirrors IAM's REST (camelCase) response shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IamEmployee(
        String userId,
        String empCode,
        String firstName,
        String lastName,
        String workEmail,
        String departmentId,
        String designationId,
        String designationName) {
}
