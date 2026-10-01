package com.sentrifugo.db.cycle;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public class CycleFinalize {

    @Column(name = "finalize_rating_scale_id")
    private UUID ratingScaleId;

    @Column(name = "finalize_notify_managers", nullable = false)
    private boolean notifyManagers = true;

    @Column(name = "finalize_notify_employees", nullable = false)
    private boolean notifyEmployees = true;

    @Column(name = "finalize_notify_hod", nullable = false)
    private boolean notifyHod = true;

    @Column(name = "finalize_notify_hr", nullable = false)
    private boolean notifyHr = true;

    public UUID getRatingScaleId() {
        return ratingScaleId;
    }

    public void setRatingScaleId(UUID ratingScaleId) {
        this.ratingScaleId = ratingScaleId;
    }

    public boolean isNotifyManagers() {
        return notifyManagers;
    }

    public void setNotifyManagers(boolean notifyManagers) {
        this.notifyManagers = notifyManagers;
    }

    public boolean isNotifyEmployees() {
        return notifyEmployees;
    }

    public void setNotifyEmployees(boolean notifyEmployees) {
        this.notifyEmployees = notifyEmployees;
    }

    public boolean isNotifyHod() {
        return notifyHod;
    }

    public void setNotifyHod(boolean notifyHod) {
        this.notifyHod = notifyHod;
    }

    public boolean isNotifyHr() {
        return notifyHr;
    }

    public void setNotifyHr(boolean notifyHr) {
        this.notifyHr = notifyHr;
    }
}
