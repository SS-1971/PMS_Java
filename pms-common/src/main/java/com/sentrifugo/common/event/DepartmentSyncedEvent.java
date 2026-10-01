package com.sentrifugo.common.event;

/** A department was created or updated in IAM. */
public record DepartmentSyncedEvent(String id, String organisationId, String name, boolean isActive) {
}
