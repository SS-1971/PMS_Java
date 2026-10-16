package com.sentrifugo.db.enums;

/** Who produced a revision the employee has to acknowledge (screen 4.5). */
public enum PmsRevisionSource {
    /** The manager raised a mid-year revision and the HOD approved it. */
    HOD_APPROVED,
    /** The manager revised the targets after the employee's change request or an HOD return. */
    MANAGER_REVISED
}
