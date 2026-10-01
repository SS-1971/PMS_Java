package com.sentrifugo.common.event;

/**
 * A plant (IAM business unit) was created or updated. Published by
 * {@code pms-messaging}'s IAM domain-events consumer, handled by
 * {@code pms-app}'s replica-sync listener — kept in {@code pms-common} so
 * both sides can reference it without {@code pms-messaging} depending on
 * {@code pms-db}.
 */
public record PlantSyncedEvent(String id, String organisationId, String name, boolean isActive) {
}
