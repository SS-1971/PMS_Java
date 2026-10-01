package com.sentrifugo.common.event;

/** A plant (IAM business unit) was soft-deleted. */
public record PlantDeletedEvent(String id) {
}
