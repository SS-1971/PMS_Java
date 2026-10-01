package com.sentrifugo.messaging.iam;

/** IAM did not reply within the RPC timeout. Subclasses {@link IamUnavailableException}
 * so callers that only catch that treat a timed-out IAM the same as an unreachable one. */
public class IamTimeoutException extends IamUnavailableException {
    public IamTimeoutException(String message) {
        super(message);
    }
}
