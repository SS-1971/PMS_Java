package com.sentrifugo.integration.rpc;

import tools.jackson.databind.JsonNode;

/**
 * The reply envelope every Python {@code RpcServer} sends back, verbatim:
 * {@code {"correlation_id": "...", "data": <result | null>, "error": <string | null>}}.
 *
 * <p>Deserialises straight off the wire because the app's Jackson 3
 * {@code JsonMapper} is already configured with
 * {@code spring.jackson.property-naming-strategy=SNAKE_CASE} — {@code correlationId}
 * maps to {@code correlation_id} with no extra annotation needed, same as every
 * other DTO in this codebase.
 */
record RpcEnvelope(String correlationId, JsonNode data, String error) {
}
