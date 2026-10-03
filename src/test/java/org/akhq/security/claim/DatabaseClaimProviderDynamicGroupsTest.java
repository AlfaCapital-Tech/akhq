package org.akhq.security.claim;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.ServerAuthentication;
import jakarta.inject.Inject;
import org.akhq.AbstractTestWithPostgres;
import org.akhq.configs.security.Group;
import org.akhq.models.accessmanagement.TopicAccessEntity;
import org.akhq.repositories.accessmanagement.TopicAccessRepository;
import org.akhq.security.authentication.UserGroupsResolver;
import org.akhq.security.authentication.mcp.McpOauthAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseClaimProviderDynamicGroupsTest extends AbstractTestWithPostgres {

    private static final String TOPIC = "test.topic-x";

    @Inject
    DatabaseClaimProvider databaseClaimProvider;

    @Inject
    UserGroupsResolver userGroupsResolver;

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
    void userGroupsResolverIsReplaced() {
        assertInstanceOf(DatabaseUserGroupsResolver.class, userGroupsResolver);
    }

    @Test
    void resolveDynamicGroups_returnsEmpty_whenNoAccesses() {
        assertTrue(databaseClaimProvider.resolveDynamicGroups("alice").isEmpty());
    }

    @Test
    void resolve_appliesGrantAndRevokeWithoutRelogin() {
        // Same token for every call: a static group plus a stale db-access group that must be ignored
        Authentication token = new ServerAuthentication("alice", List.of(), Map.of("groups", Map.of(
            "reader", List.of(Map.of("role", "topic-read", "patterns", List.of("public\\..*"))),
            DatabaseClaimProvider.DYNAMIC_GROUP_PREFIX + "alice-stale-READ", List.of(Map.of("role", "topic-read", "patterns", List.of("stale")))
        )));

        assertEquals(List.of(List.of("public\\..*")), patterns(userGroupsResolver.resolve(token)));

        TopicAccessEntity access = grant("alice");
        List<Group> afterGrant = userGroupsResolver.resolve(token);
        assertEquals(2, afterGrant.size());
        assertTrue(patterns(afterGrant).contains(List.of(Pattern.quote(TOPIC))));
        assertTrue(afterGrant.stream().allMatch(g -> g.getRole().equals("topic-read")));

        topicAccessRepository.deleteById(access.getId());
        assertEquals(List.of(List.of("public\\..*")), patterns(userGroupsResolver.resolve(token)));
    }

    @Test
    void resolve_mcpOauthGetsDynamicGroups() {
        Authentication mcp = new McpOauthAuthentication("subject-id", Map.of("preferred_username", "alice"));

        TopicAccessEntity access = grant("alice");
        assertEquals(List.of(List.of(Pattern.quote(TOPIC))), patterns(userGroupsResolver.resolve(mcp)));

        topicAccessRepository.deleteById(access.getId());
        assertTrue(userGroupsResolver.resolve(mcp).isEmpty());
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

    private TopicAccessEntity grant(String username) {
        TopicAccessEntity access = new TopicAccessEntity();
        access.setUsername(username);
        access.setTopicName(TOPIC);
        access.setRole("READ");
        access.setGrantedBy("admin");
        access.setGrantedAt(Instant.now());
        return topicAccessRepository.save(access);
    }

    private static List<List<String>> patterns(List<Group> groups) {
        return groups.stream().map(Group::getPatterns).toList();
    }
}
