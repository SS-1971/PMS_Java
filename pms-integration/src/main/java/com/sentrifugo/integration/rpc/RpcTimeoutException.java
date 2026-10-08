package com.sentrifugo.integration.rpc;

/**
 * The callee did not reply within the caller's timeout.
 *
 * <p>Subclasses {@link RpcUnavailableException} — same as Python's {@code IamTimeout}
 * subclassing {@code IamUnavailable} — so a call site that only distinguishes
 * "RPC worked" from "RPC did not work" can catch the base type, while one that
 * cares can still tell "no reply in time" apart from "transport broke".
 */
public class RpcTimeoutException extends RpcUnavailableException {

    public RpcTimeoutException(String message) {
        super(message);
    }
}
