package com.sentrifugo.pms.db.mapper;

import com.sentrifugo.pms.db.dto.PmsCycleApplicabilityDto;
import com.sentrifugo.pms.db.dto.PmsCycleDto;
import com.sentrifugo.pms.db.dto.PmsCycleFinalizeDto;
import com.sentrifugo.pms.db.dto.PmsCycleNotificationDto;
import com.sentrifugo.pms.db.dto.PmsCycleStageDto;
import com.sentrifugo.pms.db.entity.PmsCycleApplicabilityEmbeddable;
import com.sentrifugo.pms.db.entity.PmsCycleEntity;
import com.sentrifugo.pms.db.entity.PmsCycleFinalizeEmbeddable;
import com.sentrifugo.pms.db.entity.PmsCycleStageEntity;
import com.sentrifugo.pms.db.model.PmsCycleNotificationModel;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, uses = PmsMappingSupport.class,
        builder = @Builder(disableBuilder = true))
public interface PmsCycleMapper {

    @Mapping(target = "stages", source = "stages", qualifiedByName = "orderedStages")
    PmsCycleDto toDto(PmsCycleEntity entity);

    @Mapping(target = "notifyEnabled", source = "notify")
    PmsCycleStageDto toStageDto(PmsCycleStageEntity entity);

    @IgnoreBaseEntityFields
    @Mapping(target = "cycle", ignore = true)
    @Mapping(target = "notify", source = "notifyEnabled")
    PmsCycleStageEntity toStageEntity(PmsCycleStageDto dto);

    List<PmsCycleStageEntity> toStageEntities(List<PmsCycleStageDto> dtos);

    PmsCycleApplicabilityDto toApplicabilityDto(PmsCycleApplicabilityEmbeddable embeddable);

    PmsCycleApplicabilityEmbeddable toApplicabilityEmbeddable(PmsCycleApplicabilityDto dto);

    PmsCycleFinalizeDto toFinalizeDto(PmsCycleFinalizeEmbeddable embeddable);

    PmsCycleFinalizeEmbeddable toFinalizeEmbeddable(PmsCycleFinalizeDto dto);

    PmsCycleNotificationDto toNotificationDto(PmsCycleNotificationModel model);

    /** Stages are always returned in process order (enum declaration order). */
    @Named("orderedStages")
    default List<PmsCycleStageDto> orderedStages(List<PmsCycleStageEntity> stages) {
        return stages.stream()
                .sorted(Comparator.comparing(PmsCycleStageEntity::getStage))
                .map(this::toStageDto)
                .toList();
    }
}
