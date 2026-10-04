package com.sentrifugo.pms.db.mapper;

import com.sentrifugo.pms.db.dto.PmsCompetencyDto;
import com.sentrifugo.pms.db.dto.PmsCompetencyUpsertDto;
import com.sentrifugo.pms.db.entity.PmsCompetencyEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, uses = PmsMappingSupport.class,
        builder = @Builder(disableBuilder = true))
public interface PmsCompetencyMapper {

    @Mapping(target = "isActive", source = "enabled")
    PmsCompetencyDto toDto(PmsCompetencyEntity entity);

    List<PmsCompetencyDto> toDtoList(List<PmsCompetencyEntity> entities);

    @IgnoreBaseEntityFields
    @Mapping(target = "organisationId", source = "organisationId")
    @Mapping(target = "name", source = "dto.name", qualifiedByName = "trim")
    @Mapping(target = "category", source = "dto.category")
    @Mapping(target = "enabled", expression = "java(dto.isActive() == null || dto.isActive())")
    PmsCompetencyEntity toEntity(PmsCompetencyUpsertDto dto, String organisationId);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreBaseEntityFields
    @Mapping(target = "organisationId", ignore = true)
    @Mapping(target = "name", qualifiedByName = "trim")
    @Mapping(target = "enabled", source = "isActive")
    void updateEntity(PmsCompetencyUpsertDto dto, @MappingTarget PmsCompetencyEntity entity);
}
