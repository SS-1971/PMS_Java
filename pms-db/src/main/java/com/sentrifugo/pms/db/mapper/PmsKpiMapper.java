package com.sentrifugo.pms.db.mapper;

import com.sentrifugo.pms.db.dto.PmsKpiDto;
import com.sentrifugo.pms.db.dto.PmsKpiUpsertDto;
import com.sentrifugo.pms.db.entity.PmsKpiEntity;
import com.sentrifugo.pms.db.entity.PmsKraEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, uses = PmsMappingSupport.class,
        builder = @Builder(disableBuilder = true))
public interface PmsKpiMapper {

    @Mapping(target = "kraId", source = "kra.id")
    @Mapping(target = "kraName", source = "kra.name")
    PmsKpiDto toDto(PmsKpiEntity entity);

    List<PmsKpiDto> toDtoList(List<PmsKpiEntity> entities);

    /** The parent KRA is resolved (and org-checked) by the caller; the payload only carries its id. */
    @IgnoreBaseEntityFields
    @Mapping(target = "organisationId", source = "organisationId")
    @Mapping(target = "kra", source = "kra")
    @Mapping(target = "name", source = "dto.name", qualifiedByName = "trim")
    @Mapping(target = "unit", source = "dto.unit")
    @Mapping(target = "targetType", source = "dto.targetType")
    @Mapping(target = "expectedOutcome", source = "dto.expectedOutcome")
    @Mapping(target = "evidenceRequired", source = "dto.evidenceRequired")
    PmsKpiEntity toEntity(PmsKpiUpsertDto dto, String organisationId, PmsKraEntity kra);

    @IgnoreBaseEntityFields
    @Mapping(target = "organisationId", ignore = true)
    @Mapping(target = "kra", source = "kra")
    @Mapping(target = "name", source = "dto.name", qualifiedByName = "trim")
    @Mapping(target = "unit", source = "dto.unit")
    @Mapping(target = "targetType", source = "dto.targetType")
    @Mapping(target = "expectedOutcome", source = "dto.expectedOutcome")
    @Mapping(target = "evidenceRequired", source = "dto.evidenceRequired")
    void updateEntity(PmsKpiUpsertDto dto, PmsKraEntity kra, @MappingTarget PmsKpiEntity entity);
}
