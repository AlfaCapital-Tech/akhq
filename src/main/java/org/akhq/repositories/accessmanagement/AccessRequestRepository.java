package org.akhq.repositories.accessmanagement;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import org.akhq.models.accessmanagement.AccessRequestEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequestEntity, UUID> {

    List<AccessRequestEntity> findByUsername(String username);

    List<AccessRequestEntity> findByStatus(String status);

    List<AccessRequestEntity> findByUsernameAndTopicNameAndStatus(String username, String topicName, String status);

    List<AccessRequestEntity> findByUsernameAndPrefixAndStatus(String username, String prefix, String status);

    /** Pending requests whose last reminder (or creation, if none yet) is at or before the threshold. */
    @Query("SELECT r FROM AccessRequestEntity r WHERE r.status = 'PENDING' "
        + "AND COALESCE(r.lastRemindedAt, r.createdAt) <= :threshold ORDER BY r.createdAt")
    List<AccessRequestEntity> findDueForReminder(Instant threshold);

    /**
     * Atomically claims the reminder slot: returns 1 when this caller moved the marker, 0 when the request
     * is no longer pending or another replica already claimed it.
     */
    @Query("UPDATE AccessRequestEntity r SET r.lastRemindedAt = :now WHERE r.id = :id AND r.status = 'PENDING' "
        + "AND COALESCE(r.lastRemindedAt, r.createdAt) <= :threshold")
    int claimReminder(UUID id, Instant threshold, Instant now);
}
