package com.sentrifugo.db.cycle;

/** One audience's notification count from the cycle's most recent publish. */
public record CycleNotificationEntry(String audience, int sent) {
}
