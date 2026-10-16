package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** The signed-in user's bell: latest notifications and how many are unread. */
public record PmsNotificationResponse(
        @JsonProperty("unread") long unread,
        @JsonProperty("items") List<Item> items) {

    public record Item(
            @JsonProperty("id") UUID id,
            @JsonProperty("title") String title,
            @JsonProperty("message") String message,
            @JsonProperty("link_path") String linkPath,
            @JsonProperty("created_on") LocalDateTime createdOn,
            @JsonProperty("read") boolean read) {
    }
}
