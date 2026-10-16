package com.sentrifugo.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A user as IAM's {@code users_with_permission} RPC returns them. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IamUserRef(String userId, String email, String name) {
}
