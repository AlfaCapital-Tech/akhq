package org.akhq.repositories.accessmanagement;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import org.akhq.models.accessmanagement.TopicAccessEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopicAccessRepository extends JpaRepository<TopicAccessEntity, UUID> {

    List<TopicAccessEntity> findByUsername(String username);

    List<TopicAccessEntity> findByTopicName(String topicName);

    Optional<TopicAccessEntity> findByUsernameAndTopicNameAndRole(String username, String topicName, String role);

    Optional<TopicAccessEntity> findByUsernameAndPrefixAndRole(String username, String prefix, String role);

    List<TopicAccessEntity> findByUsernameAndRevokedAtIsNull(String username);

    Optional<TopicAccessEntity> findByUsernameAndTopicNameAndRoleAndRevokedAtIsNull(
            String username, String topicName, String role);

    Optional<TopicAccessEntity> findByUsernameAndPrefixAndRoleAndRevokedAtIsNull(
            String username, String prefix, String role);

    /** Marks an access revoked once; concurrent or repeated calls return 0 and preserve the first audit data. */
    @Query("UPDATE TopicAccessEntity a SET a.revokedBy = :revokedBy, a.revokedAt = :revokedAt "
        + "WHERE a.id = :id AND a.revokedAt IS NULL")
    int revoke(UUID id, String revokedBy, Instant revokedAt);
}
