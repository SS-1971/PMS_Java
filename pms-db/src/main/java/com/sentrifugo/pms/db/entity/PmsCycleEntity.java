package com.sentrifugo.pms.db.entity;

import com.sentrifugo.pms.db.enums.PmsCycleStatus;
import com.sentrifugo.pms.db.enums.PmsCycleType;
import com.sentrifugo.pms.db.model.PmsCycleNotificationModel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Cycles are cancelled (status), never deleted; {@code is_active} only hides retired rows. */
@Entity
@Table(name = "pms_cycles", schema = "pms")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PmsCycleEntity extends BaseEntity<UUID> {

    @Column(name = "organisation_id", nullable = false, length = 24, updatable = false)
    private String organisationId;

    @Column(name = "cycle_code", nullable = false, length = 20, updatable = false)
    private String cycleCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PmsCycleType type;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PmsCycleStatus status;

    @Column(name = "published_on")
    private LocalDate publishedOn;

    @Embedded
    @lombok.Builder.Default
    private PmsCycleApplicabilityEmbeddable applicability = new PmsCycleApplicabilityEmbeddable();

    @Embedded
    @lombok.Builder.Default
    private PmsCycleFinalizeEmbeddable finalizeSettings = new PmsCycleFinalizeEmbeddable();

    /** Per-audience counts from the most recent publish (screen 1.6). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notifications", columnDefinition = "jsonb", nullable = false)
    @lombok.Builder.Default
    private List<PmsCycleNotificationModel> notifications = new ArrayList<>();

    @OneToMany(mappedBy = "cycle", cascade = CascadeType.ALL, orphanRemoval = true)
    @lombok.Builder.Default
    private List<PmsCycleStageEntity> stages = new ArrayList<>();

    public void replaceStages(List<PmsCycleStageEntity> newStages) {
        stages.clear();
        for (PmsCycleStageEntity stage : newStages) {
            stage.setCycle(this);
            stages.add(stage);
        }
    }

    public void setNotifications(List<PmsCycleNotificationModel> notifications) {
        this.notifications = notifications != null ? notifications : new ArrayList<>();
    }
}
