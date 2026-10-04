package com.sentrifugo.pms.service;

import com.sentrifugo.pms.model.ratingscale.RatingScaleUpdateRequest;
import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.pms.db.dto.PmsRatingScaleDto;
import com.sentrifugo.pms.db.dto.PmsRatingScaleLookupDto;
import com.sentrifugo.pms.db.entity.PmsRatingLevelEntity;
import com.sentrifugo.pms.db.entity.PmsRatingScaleEntity;
import com.sentrifugo.pms.db.mapper.PmsRatingScaleMapper;
import com.sentrifugo.pms.db.repository.PmsRatingScaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RatingScaleService {

    private final PmsRatingScaleRepository scales;
    private final PmsRatingScaleMapper mapper;

    public RatingScaleService(PmsRatingScaleRepository scales, PmsRatingScaleMapper mapper) {
        this.mapper = mapper;
        this.scales = scales;
    }

    @Transactional(readOnly = true)
    public List<PmsRatingScaleDto> listConfig(String organisationId) {
        return mapper.toDtoList(scales.findByOrganisationIdOrderByCreatedDateAsc(organisationId));
    }

    @Transactional(readOnly = true)
    public List<PmsRatingScaleLookupDto> listLookup(String organisationId) {
        return mapper.toLookupDtoList(scales.findByOrganisationIdOrderByCreatedDateAsc(organisationId));
    }

    @Transactional
    public PmsRatingScaleDto update(String organisationId, UUID scaleId, RatingScaleUpdateRequest request) {
        PmsRatingScaleEntity scale = scales.findByIdAndOrganisationId(scaleId, organisationId)
                .orElseThrow(() -> DomainException.notFound("Rating scale not found", "PMS_RATING_SCALE_NOT_FOUND"));

        validateLevels(scale, request.levels());

        Map<Integer, PmsRatingLevelEntity> byRating = new HashMap<>();
        scale.getLevels().forEach(l -> byRating.put(l.getRating(), l));
        for (RatingScaleUpdateRequest.LevelUpdate update : request.levels()) {
            PmsRatingLevelEntity level = byRating.get(update.rating());
            level.setLabel(update.label().trim());
            level.setDefinition(update.definition());
            level.setScoreMin(update.scoreMin());
            level.setScoreMax(update.scoreMax());
            level.setColor(update.color());
        }

        scale.setShowDefinitionsToEmployees(request.showDefinitionsToEmployees());
        if (request.isDefault() && !scale.isDefaultScale()) {
            scales.findByOrganisationIdOrderByCreatedDateAsc(organisationId)
                    .forEach(other -> other.setDefaultScale(false));
        }
        scale.setDefaultScale(request.isDefault());

        return mapper.toDto(scales.save(scale));
    }

    /**
     * {@code rating} values are fixed once a scale is seeded — the UI never adds
     * or removes a level (2.10) — so the update must name exactly the scale's
     * existing ratings, once each, each with a non-overlapping [min,max] range
     * in descending order: level N's score_min must exceed level (N-1)'s
     * score_max (comparing from the highest rating down).
     */
    private void validateLevels(PmsRatingScaleEntity scale, List<RatingScaleUpdateRequest.LevelUpdate> updates) {
        var existingRatings = scale.getLevels().stream().map(PmsRatingLevelEntity::getRating).sorted().toList();
        var updateRatings = updates.stream().map(RatingScaleUpdateRequest.LevelUpdate::rating).sorted().toList();
        if (!existingRatings.equals(updateRatings)) {
            throw DomainException.unprocessable(
                    "levels must cover exactly this scale's existing rating values, each once: " + existingRatings,
                    "VALIDATION_ERROR");
        }

        List<RatingScaleUpdateRequest.LevelUpdate> descending = updates.stream()
                .sorted(Comparator.comparingInt(RatingScaleUpdateRequest.LevelUpdate::rating).reversed())
                .toList();
        for (int i = 0; i < descending.size(); i++) {
            RatingScaleUpdateRequest.LevelUpdate current = descending.get(i);
            if (current.scoreMin().compareTo(current.scoreMax()) > 0) {
                throw DomainException.unprocessable(
                        "Level " + current.rating() + ": score_min must not exceed score_max", "VALIDATION_ERROR");
            }
            if (i > 0) {
                RatingScaleUpdateRequest.LevelUpdate higher = descending.get(i - 1);
                if (current.scoreMax().compareTo(higher.scoreMin()) >= 0) {
                    throw DomainException.unprocessable(
                            "Level " + current.rating() + "'s range overlaps level " + higher.rating()
                                    + "'s — ranges must not overlap", "VALIDATION_ERROR");
                }
            }
        }
    }
}
