package com.sentrifugo.db.enums;

/** Where a mid-year transfer's period 2 goal sheet stands (screens 3.6 and 3.8). */
public enum PmsTransferStatus {
    /** Transfer recorded by HR; the new manager has not set period 2 KRAs yet. */
    PENDING,
    /** Period 2 KRAs saved but not sent. */
    DRAFT,
    /** Period 2 KRAs validated and sent for the employee to acknowledge. */
    SENT
}
