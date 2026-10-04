package com.sentrifugo.pms.db.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PmsGoalTemplateKraDto(@NotNull UUID kraId, @NotNull List<@Valid PmsGoalTemplateKpiDto> kpis) {
}
