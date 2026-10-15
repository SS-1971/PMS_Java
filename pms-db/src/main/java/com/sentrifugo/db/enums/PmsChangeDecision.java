package com.sentrifugo.db.enums;

/** What the manager decided for a change request. */
public enum PmsChangeDecision {
    /** The target was changed. */
    REVISED,
    /** The target was kept as it was. */
    UNCHANGED
}
