package com.sentrifugo.db.enums;

/** Where an employee's change request stands. */
public enum PmsChangeRequestStatus {
    /** Waiting for the manager's final target. */
    REQUESTED,
    /** The manager has sent the final target (revised or kept). */
    RESPONDED
}
