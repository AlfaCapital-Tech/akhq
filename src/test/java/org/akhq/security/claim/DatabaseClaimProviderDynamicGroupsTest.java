package org.akhq.security.claim;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Inject;
import org.akhq.AbstractTestWithPostgres;
import org.akhq.configs.security.Group;
import org.akhq.models.accessmanagement.TopicAccessEntity;
import org.akhq.repositories.accessmanagement.TopicAccessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseClaimProviderDynamicGroupsTest extends AbstractTestWithPostgres {

    @Inject
    DatabaseClaimProvider databaseClaimProvider;

    @Inject
    TopicAccessRepository topicAccessRepository;

    @NonNull
    @Override
    public Map<String, String> getProperties() {
        Map<String, String> props = new HashMap<>(super.getProperties());
        props.put("akhq.access-management.enabled", "true");
        props.put("akhq.access-management.requestable-roles[0].name", "READ");
        props.put("akhq.access-management.requestable-roles[0].label", "Read data");
        props.put("akhq.access-management.requestable-roles[0].akhq-role", "topic-read");
        props.put("akhq.access-management.notifications.enabled", "false");
        return props;
    }

    @BeforeEach
    void cleanUp() {
        topicAccessRepository.deleteAll();
    }

    @Test
    void resolveDynamicGroups_returnsEmpty_whenNoAccesses() {
        Map<String, List<Group>> groups = databaseClaimProvider.resolveDynamicGroups("alice");
        assertTrue(groups.isEmpty());
    }

    @Test
    void resolveDynamicGroups_reflectsGrantsAndRevokesWithoutCache() {
        String username = "alice";

        // Initially no access
        assertTrue(databaseClaimProvider.resolveDynamicGroups(username).isEmpty());

        // Grant access — same call must immediately see the new entry, no relogin / no cache TTL
        TopicAccessEntity access = new TopicAccessEntity();
        access.setUsername(username);
        access.setTopicName("test.topic-x");
        access.setRole("READ");
        access.setGrantedBy("admin");
        access.setGrantedAt(Instant.now());
        topicAccessRepository.save(access);

        Map<String, List<Group>> afterGrant = databaseClaimProvider.resolveDynamicGroups(username);
        assertEquals(1, afterGrant.size());
        String groupKey = afterGrant.keySet().iterator().next();
        assertTrue(groupKey.startsWith(DatabaseClaimProvider.DYNAMIC_GROUP_PREFIX));
        Group g = afterGrant.get(groupKey).get(0);
        assertEquals("topic-read", g.getRole());
        assertEquals(List.of(".*"), g.getClusters());

        // Revoke — next call returns empty again
        topicAccessRepository.deleteById(access.getId());
        assertTrue(databaseClaimProvider.resolveDynamicGroups(username).isEmpty());
    }

    @Test
    void resolveDynamicGroups_handlesPrefixGrant() {
        TopicAccessEntity access = new TopicAccessEntity();
        access.setUsername("bob");
        access.setPrefix("team-x\\..*");
        access.setRole("READ");
        access.setGrantedBy("admin");
        access.setGrantedAt(Instant.now());
        topicAccessRepository.save(access);

        Map<String, List<Group>> groups = databaseClaimProvider.resolveDynamicGroups("bob");
        assertEquals(1, groups.size());
        Group g = groups.values().iterator().next().get(0);
        assertEquals("topic-read", g.getRole());
        assertEquals(List.of("team-x\\..*"), g.getPatterns());
    }
}
