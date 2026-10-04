package com.sentrifugo.pms.db.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parent of {@link PmsRatingLevelEntity}. Only filtered by {@code is_active}
 * (no {@code @SQLDelete}): a cascading delete would physically remove the
 * levels, so a retired scale is deactivated by flipping the flag.
 */
@Entity
@Table(name = "pms_rating_scales", schema = "pms")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PmsRatingScaleEntity extends BaseEntity<UUID> {

    @Column(name = "organisation_id", nullable = false, length = 24, updatable = false)
    private String organisationId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean defaultScale;

    @Column(name = "show_definitions_to_employees", nullable = false)
    @lombok.Builder.Default
    private boolean showDefinitionsToEmployees = true;

    @OneToMany(mappedBy = "scale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rating DESC")
    @lombok.Builder.Default
    private List<PmsRatingLevelEntity> levels = new ArrayList<>();

    public void addLevel(PmsRatingLevelEntity level) {
        level.setScale(this);
        levels.add(level);
    }
}
