package com.sentrifugo.common.event;

/** A department was soft-deleted in IAM. */
public record DepartmentDeletedEvent(String id) {
}
