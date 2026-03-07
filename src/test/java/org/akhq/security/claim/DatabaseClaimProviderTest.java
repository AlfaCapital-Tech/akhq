package org.akhq.security.claim;

import io.micronaut.core.annotation.NonNull;
import org.akhq.AbstractTestWithPostgres;
import org.akhq.configs.security.Group;
import org.akhq.models.accessmanagement.TopicAccessEntity;
import org.akhq.models.security.ClaimProvider;
import org.akhq.models.security.ClaimProviderType;
import org.akhq.models.security.ClaimRequest;
import org.akhq.models.security.ClaimResponse;
import org.akhq.repositories.accessmanagement.TopicAccessRepository;
import org.junit.jupiter.api.*;

import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DatabaseClaimProviderTest extends AbstractTestWithPostgres {

    @Inject
    private ClaimProvider claimProvider;

    @Inject
    private TopicAccessRepository topicAccessRepository;

    @NonNull
    @Override
    public Map<String, String> getProperties() {
        Map<String, String> props = new HashMap<>(super.getProperties());
        props.put("akhq.access-management.enabled", "true");
        props.put("akhq.access-management.requestable-roles[0].name", "READ");
        props.put("akhq.access-management.requestable-roles[0].label", "Read data");
        props.put("akhq.access-management.requestable-roles[0].akhq-role", "topic-read");
        props.put("akhq.access-management.requestable-roles[1].name", "WRITE");
        props.put("akhq.access-management.requestable-roles[1].label", "Read and write data");
        props.put("akhq.access-management.requestable-roles[1].akhq-role", "topic-data-admin");
        props.put("akhq.access-management.notifications.enabled", "false");
        return props;
    }

    @Test
    @Order(1)
    void isDatabaseClaimProvider() {
        assertInstanceOf(DatabaseClaimProvider.class, claimProvider);
    }

    @Test
    @Order(2)
    void claimWithoutDbAccess() {
        ClaimRequest request = ClaimRequest.builder()
                .providerType(ClaimProviderType.BASIC_AUTH)
                .username("admin")
                .groups(List.of("admin"))
                .build();

        ClaimResponse response = claimProvider.generateClaim(request);

        assertNotNull(response.getGroups());
        assertTrue(response.getGroups().containsKey("admin"));
    }

    @Test
    @Order(3)
    void claimWithDbAccessAddsGroup() {
        TopicAccessEntity access = new TopicAccessEntity();
        access.setUsername("testuser");
        access.setTopicName("eis.orders");
        access.setRole("READ");
        access.setGrantedBy("admin");
        topicAccessRepository.save(access);

        ClaimRequest request = ClaimRequest.builder()
                .providerType(ClaimProviderType.BASIC_AUTH)
                .username("testuser")
                .groups(List.of("admin"))
                .build();

        ClaimResponse response = claimProvider.generateClaim(request);

        String expectedKey = "db-access-testuser-eis.orders-READ";
        assertTrue(response.getGroups().containsKey(expectedKey),
                "Expected dynamic group key: " + expectedKey + ", got: " + response.getGroups().keySet());

        List<Group> dynamicGroups = response.getGroups().get(expectedKey);
        assertEquals(1, dynamicGroups.size());
        assertEquals("topic-read", dynamicGroups.get(0).getRole());
        assertEquals(List.of("\\Qeis.orders\\E"), dynamicGroups.get(0).getPatterns());
        assertEquals(List.of(".*"), dynamicGroups.get(0).getClusters());
    }

    @Test
    @Order(4)
    void claimWithMultipleAccesses() {
        TopicAccessEntity access = new TopicAccessEntity();
        access.setUsername("multiuser");
        access.setTopicName("eis.topic1");
        access.setRole("READ");
        access.setGrantedBy("admin");
        topicAccessRepository.save(access);

        TopicAccessEntity access2 = new TopicAccessEntity();
        access2.setUsername("multiuser");
        access2.setTopicName("edo.topic2");
        access2.setRole("WRITE");
        access2.setGrantedBy("admin");
        topicAccessRepository.save(access2);

        ClaimRequest request = ClaimRequest.builder()
                .providerType(ClaimProviderType.BASIC_AUTH)
                .username("multiuser")
                .groups(List.of("admin"))
                .build();

        ClaimResponse response = claimProvider.generateClaim(request);

        assertTrue(response.getGroups().containsKey("db-access-multiuser-eis.topic1-READ"));
        assertTrue(response.getGroups().containsKey("db-access-multiuser-edo.topic2-WRITE"));

        assertEquals("topic-read",
                response.getGroups().get("db-access-multiuser-eis.topic1-READ").get(0).getRole());
        assertEquals("topic-data-admin",
                response.getGroups().get("db-access-multiuser-edo.topic2-WRITE").get(0).getRole());
    }

    @Test
    @Order(5)
    void claimPreservesOriginalGroups() {
        ClaimRequest request = ClaimRequest.builder()
                .providerType(ClaimProviderType.BASIC_AUTH)
                .username("admin")
                .groups(List.of("admin"))
                .build();

        ClaimResponse response = claimProvider.generateClaim(request);

        assertTrue(response.getGroups().containsKey("admin"),
                "Original 'admin' group should be preserved");
    }
}
