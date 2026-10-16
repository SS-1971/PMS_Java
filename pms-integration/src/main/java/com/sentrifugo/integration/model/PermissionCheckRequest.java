package com.sentrifugo.integration.model;

/**
 * Request body for IAM's {@code {environment}.iam.rpc.permission_check.queue}
 * (see {@code Sentrifugo-IAM-Admin-BE/src/rpc/permission_check.py}).
 *
 * <p>Serialises to {@code {"employee_id": ..., "organisation_id": ..., "module": ...,
 * "permission_code": ...}} — the app's global {@code spring.jackson.property-naming
 * -strategy=SNAKE_CASE} does the camelCase-to-snake_case conversion, same as every
 * other DTO here, so IAM's handler sees exactly the field names it expects.
 *
 * <p>Grouped with {@link DirectReportsRequest} and {@link IamEmployee} here in
 * {@code model} — same role (one IAM RPC call's request/reply shape), same
 * package, regardless of which IAM-specific client (here, {@code IamRpcClient})
 * happens to use it.
 */
public record PermissionCheckRequest(String employeeId, String organisationId, String module, String permissionCode) {
}
