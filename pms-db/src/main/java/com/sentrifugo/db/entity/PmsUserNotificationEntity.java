package com.sentrifugo.db.entity;

import com.sentrifugo.db.enums.PmsNotificationAudience;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/** One in-app notification for one user (the bell): written when a cycle is published, read by the user. */
@Entity
@Table(
        name = "user_notification",
        schema = "pms",
        indexes = {
                @Index(name = "idx_user_notification_user", columnList = "organisation_id, user_id"),
                @Index(name = "idx_user_notification_cycle", columnList = "cycle_id")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsUserNotificationEntity extends BaseEntity<UUID> {

    @Column(name = "organisation_id", nullable = false)
    private String organisationId;

    /** IAM user id of the recipient. */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "cycle_id")
    private UUID cycleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", length = 40)
    private PmsNotificationAudience audience;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    /** Screen the notification opens, e.g. {@code /pms/my-goals/2026}. */
    @Column(name = "link_path", length = 200)
    private String linkPath;

    /** Null while unread. */
    @Column(name = "read_on")
    private LocalDateTime readOn;
}
