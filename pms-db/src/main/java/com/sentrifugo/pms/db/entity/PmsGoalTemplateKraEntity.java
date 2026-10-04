package com.sentrifugo.pms.db.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One KRA ticked into a goal template, with the KPIs chosen under it. */
@Entity
@Table(name = "pms_goal_template_kras", schema = "pms")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PmsGoalTemplateKraEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private PmsGoalTemplateEntity template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kra_id", nullable = false)
    private PmsKraEntity kra;

    @OneToMany(mappedBy = "templateKra", cascade = CascadeType.ALL, orphanRemoval = true)
    @lombok.Builder.Default
    private List<PmsGoalTemplateKpiEntity> kpis = new ArrayList<>();

    public void addKpi(PmsGoalTemplateKpiEntity kpi) {
        kpi.setTemplateKra(this);
        kpis.add(kpi);
    }
}
