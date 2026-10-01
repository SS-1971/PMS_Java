package com.sentrifugo.messaging.iam;

/** The broker, or IAM itself, could not be reached — degrade / retry later. */
public class IamUnavailableException extends RuntimeException {
    public IamUnavailableException(String message) {
        super(message);
    }

    public IamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
