package com.sentrifugo.pms.goaltemplate;

import java.util.List;
import java.util.UUID;

public record GoalTemplateDto(UUID id, String departmentName, String designationName, String plant,
                              GoalTemplateDtos.Basic basic, List<GoalTemplateDtos.KraEntry> kras,
                              List<GoalTemplateDtos.CompetencyEntry> competencies) {
}
