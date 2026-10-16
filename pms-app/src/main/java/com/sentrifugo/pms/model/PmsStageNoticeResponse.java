package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * One cycle stage window that concerns the signed-in user: goal setting for a manager, employee acknowledgement
 * for an employee, HOD approval for an HOD. Feeds the bell and the "open until" banner on the screens.
 */
public record PmsStageNoticeResponse(
        /** {@code goal_setting}, {@code employee_acknowledgement} or {@code hod_approval}. */
        @JsonProperty("stage") String stage,
        @JsonProperty("title") String title,
        /** Who the stage is for: {@code manager}, {@code employee} or {@code hod}. */
        @JsonProperty("audience") String audience,
        @JsonProperty("cycle_name") String cycleName,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        /** {@code upcoming}, {@code open} or {@code closed}, from today against the dates. */
        @JsonProperty("state") String state,
        /** Days left including today while open; days until the start while upcoming; null once closed. */
        @JsonProperty("days_left") Long daysLeft,
        /** True when the bell should announce it: the stage notification switch and the audience switch are on. */
        @JsonProperty("notify") boolean announce) {
}
