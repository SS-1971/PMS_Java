package com.sentrifugo.db.cycle;

import com.sentrifugo.db.audit.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pms_cycles")
public class Cycle extends Auditable {

    @Column(name = "cycle_code", nullable = false, length = 20)
    private String cycleCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 20)
    private CycleType type;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, length = 20)
    private CycleStatus status;

    @Column(name = "published_on")
    private LocalDate publishedOn;

    @Embedded
    private CycleApplicability applicability = new CycleApplicability();

    @Embedded
    private CycleFinalize finalize = new CycleFinalize();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<CycleNotificationEntry> notifications = new ArrayList<>();

    @OneToMany(mappedBy = "cycle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CycleStageRow> stages = new ArrayList<>();

    protected Cycle() {
    }

    public Cycle(String organisationId, String cycleCode, String name, String description, CycleType type,
                LocalDate periodStart, LocalDate periodEnd, CycleStatus status) {
        setOrganisationId(organisationId);
        this.cycleCode = cycleCode;
        this.name = name;
        this.description = description;
        this.type = type;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.status = status;
    }

    public void replaceStages(List<CycleStageRow> newStages) {
        this.stages.clear();
        for (CycleStageRow stage : newStages) {
            stage.setCycle(this);
            this.stages.add(stage);
        }
    }

    public String getCycleCode() {
        return cycleCode;
    }

    public void setCycleCode(String cycleCode) {
        this.cycleCode = cycleCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CycleType getType() {
        return type;
    }

    public void setType(CycleType type) {
        this.type = type;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public CycleStatus getStatus() {
        return status;
    }

    public void setStatus(CycleStatus status) {
        this.status = status;
    }

    public LocalDate getPublishedOn() {
        return publishedOn;
    }

    public void setPublishedOn(LocalDate publishedOn) {
        this.publishedOn = publishedOn;
    }

    public CycleApplicability getApplicability() {
        return applicability;
    }

    public void setApplicability(CycleApplicability applicability) {
        this.applicability = applicability;
    }

    public CycleFinalize getFinalize() {
        return finalize;
    }

    public void setFinalize(CycleFinalize finalize) {
        this.finalize = finalize;
    }

    public List<CycleNotificationEntry> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<CycleNotificationEntry> notifications) {
        this.notifications = notifications != null ? notifications : new ArrayList<>();
    }

    public List<CycleStageRow> getStages() {
        return stages;
    }
}
