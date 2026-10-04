package com.sentrifugo.pms.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Rating scale + notification choices made at cycle finalisation; stored inline ({@code finalize_*} columns). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PmsCycleFinalizeEmbeddable {

    @Column(name = "finalize_rating_scale_id")
    private UUID ratingScaleId;

    @Column(name = "finalize_notify_managers", nullable = false)
    @Builder.Default
    private boolean notifyManagers = true;

    @Column(name = "finalize_notify_employees", nullable = false)
    @Builder.Default
    private boolean notifyEmployees = true;

    @Column(name = "finalize_notify_hod", nullable = false)
    @Builder.Default
    private boolean notifyHod = true;

    @Column(name = "finalize_notify_hr", nullable = false)
    @Builder.Default
    private boolean notifyHr = true;
}
