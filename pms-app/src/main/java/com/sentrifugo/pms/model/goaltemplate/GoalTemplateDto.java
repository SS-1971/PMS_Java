package com.sentrifugo.pms.model.goaltemplate;

import com.sentrifugo.pms.db.dto.PmsGoalTemplateCompetencyDto;
import com.sentrifugo.pms.db.dto.PmsGoalTemplateKraDto;

import java.util.List;
import java.util.UUID;

public record GoalTemplateDto(UUID id, String departmentName, String designationName, String plant,
                              GoalTemplateDtos.Basic basic, List<PmsGoalTemplateKraDto> kras,
                              List<PmsGoalTemplateCompetencyDto> competencies) {
}
