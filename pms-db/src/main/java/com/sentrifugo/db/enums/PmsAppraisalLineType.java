package com.sentrifugo.db.enums;

/** What an appraisal line rates. */
public enum PmsAppraisalLineType {
    /** A KPI from the employee goal sheet. */
    KRA,
    /** A competency rated by the manager and the HOD. */
    COMPETENCY,
    /** A competency rated later by Plant HR (the manager only sees the self rating). */
    HR_COMPETENCY
}
