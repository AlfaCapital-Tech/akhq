package org.akhq.controllers;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import org.akhq.AbstractTestWithPostgres;
import org.akhq.models.accessmanagement.AccessRequestEntity;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccessManagementOnboardingTest extends AbstractTestWithPostgres {

    @NonNull
    @Override
    public Map<String, String> getProperties() {
        Map<String, String> props = new HashMap<>(super.getProperties());
        props.put("akhq.access-management.enabled", "true");
        props.put("akhq.access-management.super-admins[0].username", "admin");
        props.put("akhq.access-management.super-admins[0].email", "admin@example.com");
        props.put("akhq.access-management.requestable-roles[0].name", "READ");
        props.put("akhq.access-management.requestable-roles[0].label", "Read data");
        props.put("akhq.access-management.requestable-roles[0].akhq-role", "topic-read");
        props.put("akhq.access-management.prefix-owners[0].prefix", "team-a\\..*");
        props.put("akhq.access-management.prefix-owners[0].owners[0].username", "owner-a");
        props.put("akhq.access-management.prefix-owners[0].owners[0].email", "owner-a@example.com");
        props.put("akhq.access-management.notifications.enabled", "false");
        props.put("javamail.properties.mail.smtp.host", "localhost");
        return props;
    }

    @Test
    @Order(1)
    void approverFlag() {
        assertEquals(true, me("admin").get("approver"));
        assertEquals(false, me("user").get("approver"));
    }

    @Test
    @Order(2)
    void dataAccessByStaticGroups() {
        assertEquals(true, dataAccess("admin", "test", "any.topic"));
        // "user" reads test.* only on pub.* clusters, the default group reads public.* on pub.* clusters
        assertEquals(true, dataAccess("user", "pub", "test.orders"));
        assertEquals(true, dataAccess("user", "pub", "public.events"));
        assertEquals(false, dataAccess("user", "test", "test.orders"));
        assertEquals(false, dataAccess("user", "pub", "team-a.orders"));
    }

    @Test
    @Order(3)
    void dataAccessByGrantedTopicAndPrefix() {
        grant(new AccessManagementController.CreateRequestBody("granted.topic", null, "READ", "debug"));
        grant(new AccessManagementController.CreateRequestBody(null, "team-a\\..*", "READ", "analytics"));

        assertEquals(true, dataAccess("user", "test", "granted.topic"));
        assertEquals(true, dataAccess("user", "test", "team-a.orders"));
        assertEquals(false, dataAccess("user", "test", "team-b.events"));
    }

    private Map<String, Object> me(String username) {
        return client.toBlocking().retrieve(
            HttpRequest.GET("/api/test/access-management/me").basicAuth(username, "pass"),
            Argument.mapOf(String.class, Object.class)
        );
    }

    private Object dataAccess(String username, String cluster, String topic) {
        return client.toBlocking().retrieve(
            HttpRequest.GET("/api/" + cluster + "/access-management/topic/" + topic + "/data-access")
                .basicAuth(username, "pass"),
            Argument.mapOf(String.class, Object.class)
        ).get("read");
    }

    private void grant(AccessManagementController.CreateRequestBody body) {
        AccessRequestEntity request = client.toBlocking().retrieve(
            HttpRequest.POST("/api/test/access-management/request", body).basicAuth("user", "pass"),
            AccessRequestEntity.class
        );
        client.toBlocking().exchange(
            HttpRequest.PUT("/api/test/access-management/requests/" + request.getId() + "/approve", "")
                .basicAuth("admin", "pass")
        );
    }
}
