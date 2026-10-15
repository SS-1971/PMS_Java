package com.sentrifugo.db.enums;

/** Manager's mid-year review of one employee (screens 3.10 - 3.12). */
public enum PmsMidYearStatus {
    NOT_STARTED,
    COMPLETED_NO_REVISION,
    /** A target revision was raised and is with the HOD. */
    REVISION_REQUESTED
}
