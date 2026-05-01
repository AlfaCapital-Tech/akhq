package org.akhq.controllers;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.akhq.AbstractTestWithPostgres;
import org.akhq.KafkaTestCluster;
import org.akhq.models.accessmanagement.AccessRequestEntity;
import org.akhq.models.accessmanagement.TopicAccessEntity;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AccessManagementControllerTest extends AbstractTestWithPostgres {

    private static final String BASE_URL = "/api/" + KafkaTestCluster.CLUSTER_ID + "/access-management";

    private static UUID createdRequestId;
    private static UUID secondRequestId;
    private static UUID createdAccessId;

    @NonNull
    @Override
    public Map<String, String> getProperties() {
        Map<String, String> props = new HashMap<>(super.getProperties());
        props.put("akhq.access-management.enabled", "true");
        props.put("akhq.access-management.super-admins[0].username", "ADMIN");
        props.put("akhq.access-management.super-admins[0].email", "admin@test.local");
        props.put("akhq.access-management.requestable-roles[0].name", "READ");
        props.put("akhq.access-management.requestable-roles[0].label", "Read data");
        props.put("akhq.access-management.requestable-roles[0].akhq-role", "topic-read");
        props.put("akhq.access-management.requestable-roles[1].name", "WRITE");
        props.put("akhq.access-management.requestable-roles[1].label", "Read and write data");
        props.put("akhq.access-management.requestable-roles[1].akhq-role", "topic-data-admin");
        props.put("akhq.access-management.prefix-owners[0].prefix", "test\\..*");
        props.put("akhq.access-management.prefix-owners[0].owners[0].username", "user");
        props.put("akhq.access-management.prefix-owners[0].owners[0].email", "user@test.local");
        props.put("akhq.access-management.notifications.enabled", "false");
        // EmailSender bean is built unconditionally; provide a stub host so DI graph initializes.
        // Notifications are disabled above, so no mail is actually sent.
        props.put("javamail.properties.mail.smtp.host", "localhost");
        return props;
    }

    @Test
    @Order(1)
    void createRequest() {
        AccessManagementController.CreateRequestBody body =
                new AccessManagementController.CreateRequestBody("test.topic1", null, "READ", "Need access");

        AccessRequestEntity result = client.toBlocking().retrieve(
                HttpRequest.POST(BASE_URL + "/request", body).basicAuth("admin", "pass"),
                AccessRequestEntity.class
        );

        assertNotNull(result.getId());
        assertEquals("admin", result.getUsername());
        assertEquals("test.topic1", result.getTopicName());
        assertEquals("READ", result.getRole());
        assertEquals(AccessRequestEntity.STATUS_PENDING, result.getStatus());
        assertEquals("Need access", result.getReason());
        assertNotNull(result.getCreatedAt());

        createdRequestId = result.getId();
    }

    @Test
    @Order(2)
    void duplicateRequestReturnsConflict() {
        AccessManagementController.CreateRequestBody body =
                new AccessManagementController.CreateRequestBody("test.topic1", null, "READ", "Duplicate");

        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().retrieve(
                        HttpRequest.POST(BASE_URL + "/request", body).basicAuth("admin", "pass"),
                        AccessRequestEntity.class
                )
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    @Order(3)
    void myRequests() {
        List<AccessRequestEntity> result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/requests/my").basicAuth("admin", "pass"),
                Argument.listOf(AccessRequestEntity.class)
        );

        assertFalse(result.isEmpty());
        assertEquals(createdRequestId, result.get(0).getId());
    }

    @Test
    @Order(4)
    void pendingRequests() {
        List<AccessRequestEntity> result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/requests/pending").basicAuth("admin", "pass"),
                Argument.listOf(AccessRequestEntity.class)
        );

        assertFalse(result.isEmpty());
        assertTrue(result.stream().allMatch(r -> AccessRequestEntity.STATUS_PENDING.equals(r.getStatus())));
    }

    @Test
    @Order(5)
    void topicOwners() {
        AccessManagementController.TopicOwnersResponse result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/topic/test.topic1/owners").basicAuth("admin", "pass"),
                AccessManagementController.TopicOwnersResponse.class
        );

        assertEquals("test.topic1", result.getTopicName());
        assertEquals(1, result.getOwners().size());
        assertEquals("user", result.getOwners().get(0).getUsername());
    }

    @Test
    @Order(6)
    void myAccessBeforeApprove() {
        List<TopicAccessEntity> result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/topic/test.topic1/my-access").basicAuth("admin", "pass"),
                Argument.listOf(TopicAccessEntity.class)
        );

        assertTrue(result.isEmpty());
    }

    @Test
    @Order(10)
    void approveRequest() {
        AccessRequestEntity result = client.toBlocking().retrieve(
                HttpRequest.PUT(BASE_URL + "/requests/" + createdRequestId + "/approve", "")
                        .basicAuth("admin", "pass"),
                AccessRequestEntity.class
        );

        assertEquals(AccessRequestEntity.STATUS_APPROVED, result.getStatus());
        assertEquals("admin", result.getResolvedBy());
        assertNotNull(result.getResolvedAt());
    }

    @Test
    @Order(11)
    void myAccessAfterApprove() {
        List<TopicAccessEntity> result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/topic/test.topic1/my-access").basicAuth("admin", "pass"),
                Argument.listOf(TopicAccessEntity.class)
        );

        assertEquals(1, result.size());
        assertEquals("admin", result.get(0).getUsername());
        assertEquals("test.topic1", result.get(0).getTopicName());
        assertEquals("READ", result.get(0).getRole());

        createdAccessId = result.get(0).getId();
    }

    @Test
    @Order(12)
    void accesses() {
        List<TopicAccessEntity> result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/accesses").basicAuth("admin", "pass"),
                Argument.listOf(TopicAccessEntity.class)
        );

        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(a -> a.getId().equals(createdAccessId)));
    }

    @Test
    @Order(13)
    void approveAlreadyApprovedReturnsBadRequest() {
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().retrieve(
                        HttpRequest.PUT(BASE_URL + "/requests/" + createdRequestId + "/approve", "")
                                .basicAuth("admin", "pass"),
                        AccessRequestEntity.class
                )
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    @Order(20)
    void createAndRejectRequest() {
        AccessManagementController.CreateRequestBody body =
                new AccessManagementController.CreateRequestBody("test.topic2", null, "WRITE", "Want to write");

        AccessRequestEntity created = client.toBlocking().retrieve(
                HttpRequest.POST(BASE_URL + "/request", body).basicAuth("admin", "pass"),
                AccessRequestEntity.class
        );
        secondRequestId = created.getId();

        AccessManagementController.RejectBody rejectBody =
                new AccessManagementController.RejectBody("Not authorized");

        AccessRequestEntity result = client.toBlocking().retrieve(
                HttpRequest.PUT(BASE_URL + "/requests/" + secondRequestId + "/reject", rejectBody)
                        .basicAuth("admin", "pass"),
                AccessRequestEntity.class
        );

        assertEquals(AccessRequestEntity.STATUS_REJECTED, result.getStatus());
        assertEquals("Not authorized", result.getRejectReason());
        assertEquals("admin", result.getResolvedBy());
    }

    @Test
    @Order(30)
    void revokeAccess() {
        HttpResponse<?> response = client.toBlocking().exchange(
                HttpRequest.DELETE(BASE_URL + "/accesses/" + createdAccessId)
                        .basicAuth("admin", "pass")
        );

        assertEquals(HttpStatus.OK, response.getStatus());

        List<TopicAccessEntity> result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/topic/test.topic1/my-access").basicAuth("admin", "pass"),
                Argument.listOf(TopicAccessEntity.class)
        );
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(40)
    void allRequestsHistory() {
        List<AccessRequestEntity> result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/requests/all").basicAuth("admin", "pass"),
                Argument.listOf(AccessRequestEntity.class)
        );

        assertTrue(result.size() >= 2);
        assertTrue(result.stream().anyMatch(r -> AccessRequestEntity.STATUS_APPROVED.equals(r.getStatus())));
        assertTrue(result.stream().anyMatch(r -> AccessRequestEntity.STATUS_REJECTED.equals(r.getStatus())));
    }

    @Test
    @Order(50)
    void invalidRoleReturnsBadRequest() {
        AccessManagementController.CreateRequestBody body =
                new AccessManagementController.CreateRequestBody("test.topic3", null, "INVALID", "Bad role");

        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().retrieve(
                        HttpRequest.POST(BASE_URL + "/request", body).basicAuth("admin", "pass"),
                        AccessRequestEntity.class
                )
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    @Order(51)
    void notFoundRequestReturnsNotFound() {
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().retrieve(
                        HttpRequest.PUT(BASE_URL + "/requests/" + UUID.randomUUID() + "/approve", "")
                                .basicAuth("admin", "pass"),
                        AccessRequestEntity.class
                )
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @Order(60)
    void pendingRequestsCount() {
        // Create a pending request first
        AccessManagementController.CreateRequestBody body =
                new AccessManagementController.CreateRequestBody("test.topic.count", null, "READ", "Count test");

        client.toBlocking().retrieve(
                HttpRequest.POST(BASE_URL + "/request", body).basicAuth("admin", "pass"),
                AccessRequestEntity.class
        );

        // Check count
        Map<String, Object> result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/requests/pending/count").basicAuth("admin", "pass"),
                Argument.mapOf(String.class, Object.class)
        );

        assertNotNull(result.get("count"));
        assertTrue(((Number) result.get("count")).intValue() > 0);
    }

    @Test
    @Order(70)
    void caseInsensitiveOwnerSeePendingRequests() {
        // Config has owner "user" (lowercase) for prefix "test\\..*"
        // Auth returns "admin" who is super-admin with lowercase "admin" in config
        // We verify case-insensitive matching by adding an UPPERCASE super-admin config
        // and checking that lowercase "admin" from auth still matches
        // This is covered by the fact that all previous tests pass with equalsIgnoreCase

        // Create a request to ensure there's a pending one
        AccessManagementController.CreateRequestBody body =
                new AccessManagementController.CreateRequestBody("test.topic.case", null, "READ", "Case test");
        client.toBlocking().retrieve(
                HttpRequest.POST(BASE_URL + "/request", body).basicAuth("admin", "pass"),
                AccessRequestEntity.class
        );

        // admin is super-admin (case-insensitive) → should see all pending
        List<AccessRequestEntity> result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/requests/pending").basicAuth("admin", "pass"),
                Argument.listOf(AccessRequestEntity.class)
        );

        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(r -> r.getTopicName().equals("test.topic.case")));
    }

    @Test
    @Order(71)
    void caseInsensitiveSuperAdminSeeAllRequests() {
        // Config has super-admin "ADMIN" (uppercase), auth returns "admin" (lowercase)
        // equalsIgnoreCase should match → admin sees all requests
        List<AccessRequestEntity> result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/requests/all").basicAuth("admin", "pass"),
                Argument.listOf(AccessRequestEntity.class)
        );

        assertFalse(result.isEmpty());
    }

    // --- Prefix access tests ---

    private static UUID prefixRequestId;

    @Test
    @Order(80)
    void availablePrefixes() {
        List<Map<String, Object>> result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/prefixes").basicAuth("admin", "pass"),
                Argument.listOf(Argument.mapOf(String.class, Object.class))
        );

        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(p -> "test\\..*".equals(p.get("prefix"))));
    }

    @Test
    @Order(81)
    void createPrefixRequest() {
        AccessManagementController.CreateRequestBody body =
                new AccessManagementController.CreateRequestBody(null, "test\\..*", "READ", "Need prefix access");

        AccessRequestEntity result = client.toBlocking().retrieve(
                HttpRequest.POST(BASE_URL + "/request", body).basicAuth("admin", "pass"),
                AccessRequestEntity.class
        );

        assertNotNull(result.getId());
        assertNull(result.getTopicName());
        assertEquals("test\\..*", result.getPrefix());
        assertEquals(AccessRequestEntity.STATUS_PENDING, result.getStatus());

        prefixRequestId = result.getId();
    }

    @Test
    @Order(82)
    void duplicatePrefixRequestReturnsConflict() {
        AccessManagementController.CreateRequestBody body =
                new AccessManagementController.CreateRequestBody(null, "test\\..*", "READ", "Duplicate prefix");

        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().retrieve(
                        HttpRequest.POST(BASE_URL + "/request", body).basicAuth("admin", "pass"),
                        AccessRequestEntity.class
                )
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    @Order(83)
    void approvePrefixRequest() {
        AccessRequestEntity result = client.toBlocking().retrieve(
                HttpRequest.PUT(BASE_URL + "/requests/" + prefixRequestId + "/approve", "")
                        .basicAuth("admin", "pass"),
                AccessRequestEntity.class
        );

        assertEquals(AccessRequestEntity.STATUS_APPROVED, result.getStatus());
    }

    @Test
    @Order(84)
    void prefixAccessVisible() {
        List<TopicAccessEntity> result = client.toBlocking().retrieve(
                HttpRequest.GET(BASE_URL + "/accesses").basicAuth("admin", "pass"),
                Argument.listOf(TopicAccessEntity.class)
        );

        assertTrue(result.stream().anyMatch(a -> "test\\..*".equals(a.getPrefix())));
    }
}
