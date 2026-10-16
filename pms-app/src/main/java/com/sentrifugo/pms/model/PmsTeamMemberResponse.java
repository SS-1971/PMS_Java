package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * One row of screen 3.1 "My Team". {@code goal_status} is the goal-setting state for the cycle:
 * {@code not_started}, {@code draft}, {@code sent_to_employee}, {@code acknowledged}, {@code change_requested},
 * {@code with_hod}, {@code approved}, plus {@code returned_by_hod} (HOD sent the sheet back) and
 * {@code transferred_in} (moved into the team mid-year, period 2 KRAs still to set).
 */
public record PmsTeamMemberResponse(
        @JsonProperty("user_id") String userId,
        @JsonProperty("emp_code") String empCode,
        @JsonProperty("name") String name,
        @JsonProperty("email") String email,
        @JsonProperty("designation_id") String designationId,
        @JsonProperty("designation_name") String designationName,
        @JsonProperty("goal_status") String goalStatus,
        /**
         * The goal template that applies to the employee: the one assigned to them, or (until assigned) the
         * active template of their role. Null when the role has no active template. Send it back as
         * {@code template_id} when assigning.
         */
        @JsonProperty("template_id") UUID templateId,
        @JsonProperty("template_name") String templateName,
        /** True once the template is assigned to this employee ("Create &amp; assign template" or goals started). */
        @JsonProperty("template_assigned") boolean templateAssigned) {
}
