package com.sentrifugo.pms.ratingscale;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.config.RatingLevel;
import com.sentrifugo.db.config.RatingScale;
import com.sentrifugo.db.config.RatingScaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RatingScaleService {

    private final RatingScaleRepository scales;

    public RatingScaleService(RatingScaleRepository scales) {
        this.scales = scales;
    }

    public List<RatingScaleConfigDto> listConfig(String organisationId) {
        return scales.findByOrganisationIdAndDeletedOnIsNullOrderByCreatedOnAsc(organisationId).stream()
                .map(RatingScaleConfigDto::from).toList();
    }

    public List<RatingScaleLookupDto> listLookup(String organisationId) {
        return scales.findByOrganisationIdAndDeletedOnIsNullOrderByCreatedOnAsc(organisationId).stream()
                .map(RatingScaleLookupDto::from).toList();
    }

    @Transactional
    public RatingScaleConfigDto update(String organisationId, UUID scaleId, RatingScaleUpdateRequest request) {
        RatingScale scale = scales.findByIdAndOrganisationIdAndDeletedOnIsNull(scaleId, organisationId)
                .orElseThrow(() -> DomainException.notFound("Rating scale not found", "PMS_RATING_SCALE_NOT_FOUND"));

        validateLevels(scale, request.levels());

        Map<Integer, RatingLevel> byRating = new HashMap<>();
        scale.getLevels().forEach(l -> byRating.put(l.getRating(), l));
        for (RatingScaleUpdateRequest.LevelUpdate update : request.levels()) {
            RatingLevel level = byRating.get(update.rating());
            level.setLabel(update.label().trim());
            level.setDefinition(update.definition());
            level.setScoreMin(update.scoreMin());
            level.setScoreMax(update.scoreMax());
            level.setColor(update.color());
        }

        scale.setShowDefinitionsToEmployees(request.showDefinitionsToEmployees());
        if (request.isDefault() && !scale.isDefault()) {
            scales.findByOrganisationIdAndDeletedOnIsNullOrderByCreatedOnAsc(organisationId)
                    .forEach(other -> other.setDefault(false));
        }
        scale.setDefault(request.isDefault());

        return RatingScaleConfigDto.from(scales.save(scale));
    }

    /**
     * {@code rating} values are fixed once a scale is seeded — the UI never adds
     * or removes a level (2.10) — so the update must name exactly the scale's
     * existing ratings, once each, each with a non-overlapping [min,max] range
     * in descending order: level N's score_min must exceed level (N-1)'s
     * score_max (comparing from the highest rating down).
     */
    private void validateLevels(RatingScale scale, List<RatingScaleUpdateRequest.LevelUpdate> updates) {
        var existingRatings = scale.getLevels().stream().map(RatingLevel::getRating).sorted().toList();
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
