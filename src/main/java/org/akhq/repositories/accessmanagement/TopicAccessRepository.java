package org.akhq.repositories.accessmanagement;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import org.akhq.models.accessmanagement.TopicAccessEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopicAccessRepository extends JpaRepository<TopicAccessEntity, UUID> {

    List<TopicAccessEntity> findByUsername(String username);

    List<TopicAccessEntity> findByTopicName(String topicName);

    Optional<TopicAccessEntity> findByUsernameAndTopicNameAndRole(String username, String topicName, String role);
}
