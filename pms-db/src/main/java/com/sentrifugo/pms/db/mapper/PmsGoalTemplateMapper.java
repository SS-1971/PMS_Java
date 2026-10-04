package com.sentrifugo.pms.db.mapper;

import com.sentrifugo.pms.db.dto.PmsGoalTemplateCompetencyDto;
import com.sentrifugo.pms.db.dto.PmsGoalTemplateDto;
import com.sentrifugo.pms.db.dto.PmsGoalTemplateKpiDto;
import com.sentrifugo.pms.db.dto.PmsGoalTemplateKraDto;
import com.sentrifugo.pms.db.entity.PmsGoalTemplateCompetencyEntity;
import com.sentrifugo.pms.db.entity.PmsGoalTemplateEntity;
import com.sentrifugo.pms.db.entity.PmsGoalTemplateKpiEntity;
import com.sentrifugo.pms.db.entity.PmsGoalTemplateKraEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Entity -> DTO only. Building template children from a payload needs
 * org-scoped lookups of the referenced KRA/KPI/competency rows, which is the
 * service's job, so there is deliberately no DTO -> entity direction here.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, uses = PmsMappingSupport.class,
        builder = @Builder(disableBuilder = true))
public interface PmsGoalTemplateMapper {

    PmsGoalTemplateDto toDto(PmsGoalTemplateEntity entity);

    @Mapping(target = "kraId", source = "kra.id")
    PmsGoalTemplateKraDto toKraDto(PmsGoalTemplateKraEntity entity);

    @Mapping(target = "kpiId", source = "kpi.id")
    PmsGoalTemplateKpiDto toKpiDto(PmsGoalTemplateKpiEntity entity);

    @Mapping(target = "competencyId", source = "competency.id")
    PmsGoalTemplateCompetencyDto toCompetencyDto(PmsGoalTemplateCompetencyEntity entity);
}
