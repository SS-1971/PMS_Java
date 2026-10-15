package com.sentrifugo.integration.model;

/**
 * Request body for IAM's {@code users_with_permission} RPC queue: who explicitly holds a (module, permission)
 * grant. Serialises to {@code {"module", "permission", "organisation_id"}}.
 */
public record UsersWithPermissionRequest(String module, String permission, String organisationId) {
}
