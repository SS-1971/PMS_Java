package com.sentrifugo.pms.db.model;

/** One audience's notification count from a cycle's most recent publish (stored as JSONB, not an entity). */
public record PmsCycleNotificationModel(String audience, int sent) {
}
