package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Result of assigning a role template to the manager's reports. */
public record PmsTemplateAssignmentResponse(
        @JsonProperty("designation_id") String designationId,
        @JsonProperty("template_id") UUID templateId,
        @JsonProperty("template_name") String templateName,
        /** Employees who got the template just now. */
        @JsonProperty("assigned") int assigned,
        /** Employees in the role who already had a template and keep it. */
        @JsonProperty("already_assigned") int alreadyAssigned) {
}
