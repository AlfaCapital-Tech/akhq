package org.akhq.repositories.accessmanagement;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import org.akhq.models.accessmanagement.AccessRequestEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequestEntity, UUID> {

    List<AccessRequestEntity> findByUsername(String username);

    List<AccessRequestEntity> findByStatus(String status);

    List<AccessRequestEntity> findByUsernameAndTopicNameAndStatus(String username, String topicName, String status);

    List<AccessRequestEntity> findByUsernameAndPrefixAndStatus(String username, String prefix, String status);
}
