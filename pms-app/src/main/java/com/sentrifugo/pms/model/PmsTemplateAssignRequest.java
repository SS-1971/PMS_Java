package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** "Create &amp; assign template": give the chosen role template to every report in that role. */
public record PmsTemplateAssignRequest(
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear,
        /** The role (designation) whose employees get the template. */
        @JsonProperty("designation_id") @NotBlank String designationId,
        /** The template to assign: the role's active template for the year (the team list returns its id). */
        @JsonProperty("template_id") @NotNull UUID templateId) {
}
