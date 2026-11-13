package org.akhq.repositories.accessmanagement;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.akhq.AbstractTestWithPostgres;
import org.akhq.models.accessmanagement.TopicAccessEntity;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TopicAccessRepositoryTest extends AbstractTestWithPostgres {

    @Inject
    TopicAccessRepository repository;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Order(1)
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void historyAllowsRegrantAndOnlyOneActiveAccess(boolean prefix) {
        TopicAccessEntity first = repository.saveAndFlush(access(prefix));
        Instant firstRevokedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        assertEquals(1, repository.revoke(first.getId(), "owner-a", firstRevokedAt));
        assertEquals(0, repository.revoke(first.getId(), "owner-b", Instant.now()));

        TopicAccessEntity second = repository.saveAndFlush(access(prefix));
        assertNotEquals(first.getId(), second.getId());
        assertEquals(1, repository.revoke(second.getId(), "owner-a", Instant.now()));
        TopicAccessEntity third = repository.saveAndFlush(access(prefix));

        entityManager.clear();
        List<TopicAccessEntity> all = repository.findAll();
        assertEquals(3, all.size());
        assertEquals(2, all.stream().filter(a -> a.getRevokedAt() != null).count());
        assertEquals(firstRevokedAt, repository.findById(first.getId()).orElseThrow().getRevokedAt());
        assertEquals("owner-a", repository.findById(first.getId()).orElseThrow().getRevokedBy());

        List<TopicAccessEntity> active = repository.findByUsernameAndRevokedAtIsNull("alice");
        assertEquals(1, active.size());
        assertEquals(third.getId(), active.get(0).getId());
        assertEquals(third.getId(), activeDuplicate(prefix).orElseThrow().getId());
    }

    @Order(2)
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void duplicateActiveAccessIsRejected(boolean prefix) {
        repository.saveAndFlush(access(prefix));
        assertThrows(ConstraintViolationException.class, () -> repository.saveAndFlush(access(prefix)));
    }

    private java.util.Optional<TopicAccessEntity> activeDuplicate(boolean prefix) {
        return prefix
                ? repository.findByUsernameAndPrefixAndRoleAndRevokedAtIsNull("alice", "team-a\\..*", "READ")
                : repository.findByUsernameAndTopicNameAndRoleAndRevokedAtIsNull("alice", "team-a.orders", "READ");
    }

    private static TopicAccessEntity access(boolean prefix) {
        TopicAccessEntity access = new TopicAccessEntity();
        access.setUsername("alice");
        access.setRole("READ");
        access.setGrantedBy("admin");
        if (prefix) {
            access.setPrefix("team-a\\..*");
        } else {
            access.setTopicName("team-a.orders");
        }
        return access;
    }
}
