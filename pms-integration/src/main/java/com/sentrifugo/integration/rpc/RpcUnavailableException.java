package com.sentrifugo.integration.rpc;

/**
 * The RPC call could not be completed — broker unreachable, publish failed, or
 * the reply could not be decoded. Mirrors the Python suite's {@code IamUnavailable}
 * base class: every failure mode a caller needs to treat as "this callee answered
 * nothing" raises this (or a subclass), never an empty/null result that reads as
 * an authoritative answer.
 */
public class RpcUnavailableException extends RuntimeException {

    public RpcUnavailableException(String message) {
        super(message);
    }

    public RpcUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
