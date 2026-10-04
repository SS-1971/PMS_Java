package com.sentrifugo.pms.db.mapper;

import com.sentrifugo.pms.db.dto.PmsRatingLevelDto;
import com.sentrifugo.pms.db.dto.PmsRatingScaleDto;
import com.sentrifugo.pms.db.dto.PmsRatingScaleLookupDto;
import com.sentrifugo.pms.db.entity.PmsRatingLevelEntity;
import com.sentrifugo.pms.db.entity.PmsRatingScaleEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR,
        builder = @Builder(disableBuilder = true))
public interface PmsRatingScaleMapper {

    @Mapping(target = "isDefault", source = "defaultScale")
    PmsRatingScaleDto toDto(PmsRatingScaleEntity entity);

    List<PmsRatingScaleDto> toDtoList(List<PmsRatingScaleEntity> entities);

    PmsRatingLevelDto toLevelDto(PmsRatingLevelEntity entity);

    PmsRatingScaleLookupDto toLookupDto(PmsRatingScaleEntity entity);

    List<PmsRatingScaleLookupDto> toLookupDtoList(List<PmsRatingScaleEntity> entities);

    @Mapping(target = "value", source = "rating")
    @Mapping(target = "description", source = "definition")
    PmsRatingScaleLookupDto.Level toLookupLevel(PmsRatingLevelEntity entity);
}
