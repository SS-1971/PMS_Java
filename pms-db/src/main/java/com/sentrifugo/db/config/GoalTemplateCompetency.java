package com.sentrifugo.db.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** One competency ticked into a goal template, with its weight. */
@Entity
@Table(name = "pms_goal_template_competencies")
public class GoalTemplateCompetency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private GoalTemplate template;

    @ManyToOne(optional = false)
    @JoinColumn(name = "competency_id", nullable = false)
    private Competency competency;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    protected GoalTemplateCompetency() {
    }

    public GoalTemplateCompetency(Competency competency, BigDecimal weight) {
        this.competency = competency;
        this.weight = weight;
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

    public Competency getCompetency() {
        return competency;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }
}
