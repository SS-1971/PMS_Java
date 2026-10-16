package com.sentrifugo.integration.rpc;

/**
 * The callee replied, but its envelope carried an {@code error} (its handler
 * raised), or the reply body was not the envelope shape at all.
 *
 * <p>Mirrors Python's {@code IamError}: also an {@link RpcUnavailableException},
 * so callers degrade the same way for "the handler blew up" as for "the broker
 * is down" unless they specifically need the distinction (e.g. for logging).
 */
public class RpcErrorException extends RpcUnavailableException {

    public RpcErrorException(String message) {
        super(message);
    }

    public RpcErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
