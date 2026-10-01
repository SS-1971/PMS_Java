package com.sentrifugo.common.event;

/** A designation was created or updated in IAM. */
public record DesignationSyncedEvent(String id, String organisationId, String name, String departmentId, boolean isActive) {
}
