package com.sentrifugo.db.config;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One KRA ticked into a goal template, with the KPIs chosen under it. */
@Entity
@Table(name = "pms_goal_template_kras")
public class GoalTemplateKra {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private GoalTemplate template;

    @ManyToOne(optional = false)
    @JoinColumn(name = "kra_id", nullable = false)
    private Kra kra;

    @OneToMany(mappedBy = "templateKra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoalTemplateKpi> kpis = new ArrayList<>();

    protected GoalTemplateKra() {
    }

    public GoalTemplateKra(Kra kra) {
        this.kra = kra;
    }

    public void addKpi(GoalTemplateKpi kpi) {
        kpi.setTemplateKra(this);
        this.kpis.add(kpi);
    }

    public UUID getId() {
        return id;
    }

    public GoalTemplate getTemplate() {
        return template;
    }

    public void setTemplate(GoalTemplate template) {
        this.template = template;
    }

    public Kra getKra() {
        return kra;
    }

    public List<GoalTemplateKpi> getKpis() {
        return kpis;
    }
}
