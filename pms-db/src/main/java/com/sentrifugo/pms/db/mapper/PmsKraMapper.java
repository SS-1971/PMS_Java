package com.sentrifugo.pms.db.mapper;

import com.sentrifugo.pms.db.dto.PmsKraDto;
import com.sentrifugo.pms.db.dto.PmsKraUpsertDto;
import com.sentrifugo.pms.db.entity.PmsKraEntity;
import org.mapstruct.Builder;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, uses = PmsMappingSupport.class,
        builder = @Builder(disableBuilder = true))
public interface PmsKraMapper {

    PmsKraDto toDto(PmsKraEntity entity);

    List<PmsKraDto> toDtoList(List<PmsKraEntity> entities);

    /** {@code organisationId} comes from the authenticated session, never from the payload. */
    @IgnoreBaseEntityFields
    @Mapping(target = "organisationId", source = "organisationId")
    @Mapping(target = "name", source = "dto.name", qualifiedByName = "trim")
    PmsKraEntity toEntity(PmsKraUpsertDto dto, String organisationId);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreBaseEntityFields
    @Mapping(target = "organisationId", ignore = true)
    @Mapping(target = "name", qualifiedByName = "trim")
    void updateEntity(PmsKraUpsertDto dto, @MappingTarget PmsKraEntity entity);
}
