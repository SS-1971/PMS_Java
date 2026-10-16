package com.sentrifugo.integration.model;

/**
 * Request body for IAM's {@code {environment}.iam.rpc.direct_reports.queue}
 * (see {@code Sentrifugo-IAM-Admin-BE/src/rpc/direct_reports.py}).
 *
 * <p>Serialises to {@code {"manager_id": ..., "organisation_id": ...}} via the
 * app's global snake_case Jackson config, matching IAM's handler exactly.
 */
public record DirectReportsRequest(String managerId, String organisationId) {
}
