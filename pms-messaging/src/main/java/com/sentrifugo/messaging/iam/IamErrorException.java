package com.sentrifugo.messaging.iam;

/** IAM replied, but the envelope carried an {@code error} (the handler raised). */
public class IamErrorException extends IamUnavailableException {
    public IamErrorException(String message) {
        super(message);
    }
}
