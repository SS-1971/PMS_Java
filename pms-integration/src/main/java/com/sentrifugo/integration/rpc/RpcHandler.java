package com.sentrifugo.integration.rpc;

import tools.jackson.databind.JsonNode;

/**
 * Handles one RPC request's decoded JSON payload and returns the value that
 * becomes {@code data} in the reply envelope.
 *
 * <p>Mirrors the Python suite's {@code Handler} type alias in
 * {@code rpc/server.py}: throwing is fine here — {@link RpcServer} turns it
 * into an {@code error} envelope, not a hang, same as the Python version's
 * "handlers receive the decoded request payload and return whatever should
 * land in {@code data}".
 */
@FunctionalInterface
public interface RpcHandler {

    Object handle(JsonNode payload) throws Exception;
}
