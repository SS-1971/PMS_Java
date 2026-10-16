package com.sentrifugo.db.enums;

/** Which reading of an actual a progress entry is. */
public enum PmsProgressPeriod {
    /** Running value, updated through the year (screen 3.9). */
    CURRENT,
    /** Half-year snapshot used by the mid-year review (screens 3.10 - 3.12). */
    H1,
    /** Year-end actual used by the appraisal (screens 3.13 - 3.14). */
    FY
}
