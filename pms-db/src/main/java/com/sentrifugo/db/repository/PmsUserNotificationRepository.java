package com.sentrifugo.db.repository;

import com.sentrifugo.db.entity.PmsUserNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PmsUserNotificationRepository extends JpaRepository<PmsUserNotificationEntity, UUID> {

    /** The user's latest notifications, newest first. */
    List<PmsUserNotificationEntity> findTop50ByOrganisationIdAndUserIdOrderByCreatedDateDesc(
            String organisationId, String userId);

    long countByOrganisationIdAndUserIdAndReadOnIsNull(String organisationId, String userId);

    List<PmsUserNotificationEntity> findByOrganisationIdAndUserIdAndReadOnIsNull(String organisationId,
                                                                               String userId);

    List<PmsUserNotificationEntity> findByOrganisationIdAndUserIdAndIdIn(String organisationId, String userId,
                                                                        Collection<UUID> ids);
}
