package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/** Marks notifications read; an empty or missing list marks every unread one. */
public record PmsNotificationReadRequest(@JsonProperty("ids") List<UUID> ids) {
}
