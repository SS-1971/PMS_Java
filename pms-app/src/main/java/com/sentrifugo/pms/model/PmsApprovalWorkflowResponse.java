package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/** An approval workflow with its ordered approver steps. */
public record PmsApprovalWorkflowResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("steps") List<String> steps,
        @JsonProperty("status") String status) {
}
