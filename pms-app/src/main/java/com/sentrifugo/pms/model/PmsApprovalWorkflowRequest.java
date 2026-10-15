package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Add / edit an approval workflow (Configuration, Approval Workflow). {@code status}: active | inactive. */
public record PmsApprovalWorkflowRequest(
        @JsonProperty("name") @NotBlank @Size(max = 200) String name,
        @JsonProperty("description") @Size(max = 500) String description,
        /** Approver labels in order. */
        @JsonProperty("steps") @NotEmpty List<@NotBlank @Size(max = 100) String> steps,
        @JsonProperty("status") String status) {
}
