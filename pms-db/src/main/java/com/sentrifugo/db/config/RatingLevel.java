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

/** One rating value on a scale. {@code rating} is fixed once seeded — the UI edits
 * label/definition/range/colour only, never adds or removes a level (2.10). */
@Entity
@Table(name = "pms_rating_levels")
public class RatingLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "scale_id", nullable = false)
    private RatingScale scale;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, length = 40)
    private String label;

    @Column(length = 200)
    private String definition;

    @Column(name = "score_min", nullable = false, precision = 4, scale = 2)
    private BigDecimal scoreMin;

    @Column(name = "score_max", nullable = false, precision = 4, scale = 2)
    private BigDecimal scoreMax;

    @Column(nullable = false, length = 7)
    private String color;

    protected RatingLevel() {
    }

    public RatingLevel(int rating, String label, String definition, BigDecimal scoreMin, BigDecimal scoreMax,
                       String color) {
        this.rating = rating;
        this.label = label;
        this.definition = definition;
        this.scoreMin = scoreMin;
        this.scoreMax = scoreMax;
        this.color = color;
    }

    public UUID getId() {
        return id;
    }

    public RatingScale getScale() {
        return scale;
    }

    public void setScale(RatingScale scale) {
        this.scale = scale;
    }

    public int getRating() {
        return rating;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public BigDecimal getScoreMin() {
        return scoreMin;
    }

    public void setScoreMin(BigDecimal scoreMin) {
        this.scoreMin = scoreMin;
    }

    public BigDecimal getScoreMax() {
        return scoreMax;
    }

    public void setScoreMax(BigDecimal scoreMax) {
        this.scoreMax = scoreMax;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
