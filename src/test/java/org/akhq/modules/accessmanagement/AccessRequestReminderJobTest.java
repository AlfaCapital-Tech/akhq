package org.akhq.modules.accessmanagement;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.email.BodyType;
import io.micronaut.email.Email;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Inject;
import org.akhq.AbstractTestWithPostgres;
import org.akhq.KafkaTestCluster;
import org.akhq.configs.accessmanagement.AccessManagementProperties;
import org.akhq.controllers.AccessManagementController;
import org.akhq.models.accessmanagement.AccessRequestEntity;
import org.akhq.repositories.accessmanagement.AccessRequestRepository;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessRequestReminderJobTest extends AbstractTestWithPostgres {

    private static final String BASE_URL = "/api/" + KafkaTestCluster.CLUSTER_ID + "/access-management";
    private static final String PUBLIC_URL = "https://akhq.example.com";

    @Inject
    AccessRequestReminderJob job;

    @Inject
    AccessRequestRepository repository;

    @Inject
    NotificationService notificationService;

    @Inject
    AccessManagementProperties properties;

    @Inject
    RecordingEmailSender mailbox;

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
        props.put("akhq.access-management.notifications.enabled", "true");
        props.put("akhq.access-management.notifications.base-url", PUBLIC_URL);
        props.put("akhq.access-management.notifications.reminder-interval", "3d");
        // keep the scheduler out of the way: tests call run() directly
        props.put("akhq.access-management.notifications.reminder-initial-delay", "1h");
        props.put("akhq.test.record-email", "true");
        props.put("javamail.properties.mail.smtp.host", "localhost");
        return props;
    }

    @BeforeEach
    void clearMailbox() {
        mailbox.sent.clear();
    }

    private final List<UUID> createdIds = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** The Postgres container is shared by all test classes: leave only our own requests behind, i.e. none. */
    @AfterAll
    void cleanUp() {
        createdIds.forEach(id -> repository.findById(id).ifPresent(repository::delete));
    }

    private AccessRequestEntity pendingRequest(String user, String topic, Instant createdAt, String cluster) {
        AccessRequestEntity e = new AccessRequestEntity();
        e.setUsername(user);
        e.setTopicName(topic);
        e.setRole("READ");
        e.setStatus(AccessRequestEntity.STATUS_PENDING);
        e.setCreatedAt(createdAt);
        e.setCluster(cluster);
        AccessRequestEntity saved = repository.save(e);
        createdIds.add(saved.getId());
        return saved;
    }

    private static String text(Email email) {
        return email.getBody().get(BodyType.TEXT).orElseThrow();
    }

    @Test
    @Order(1)
    void newRequestEmailLinksToTheRequestInItsCluster() {
        var body = new AccessManagementController.CreateRequestBody("team-a.orders", null, "READ", "need it");
        AccessRequestEntity created = client.toBlocking().retrieve(
                HttpRequest.POST(BASE_URL + "/request", body).basicAuth("admin", "pass"),
                AccessRequestEntity.class);

        createdIds.add(created.getId());
        assertEquals(KafkaTestCluster.CLUSTER_ID, created.getCluster());
        assertEquals(1, mailbox.sent.size());
        Email email = mailbox.sent.get(0);
        assertEquals("owner-a@example.com", email.getTo().iterator().next().getEmail());
        assertEquals("[AKHQ] New access request from admin", email.getSubject());
        String link = PUBLIC_URL + "/ui/" + KafkaTestCluster.CLUSTER_ID + "/access-management?request=" + created.getId();
        assertTrue(text(email).contains(link), text(email));
        assertTrue(email.getBody().get(BodyType.HTML).orElseThrow().contains("href=\"" + link + "\""));
    }

    @Test
    @Order(2)
    void legacyRequestWithoutClusterLinksToFirstConfiguredCluster() {
        AccessRequestEntity legacy = new AccessRequestEntity();
        legacy.setId(UUID.randomUUID());
        legacy.setCluster(null);

        assertEquals(PUBLIC_URL + "/ui/" + KafkaTestCluster.CLUSTER_ID + "/access-management?request=" + legacy.getId(),
                notificationService.requestUrl(legacy));
    }

    @Test
    @Order(3)
    void reminderGoesOutOnceForOverduePendingAndStopsAfterDecision() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        AccessRequestEntity overdue = pendingRequest("alice", "team-a.orders", now.minus(Duration.ofDays(4)), "prod");
        AccessRequestEntity fresh = pendingRequest("bob", "team-a.events", now.minus(Duration.ofHours(1)), "prod");
        AccessRequestEntity noOwner = pendingRequest("carol", "other.topic", now.minus(Duration.ofDays(10)), null);

        // the Postgres container is shared with other test classes, so count only our own requests
        int sentNow = job.run(now);
        assertTrue(sentNow >= 2, "overdue request with owner + overdue request without owner, got " + sentNow);
        assertTrue(mailbox.sent.stream().noneMatch(e -> e.getSubject().contains("from bob")), "fresh request not reminded");

        Email toOwner = mailbox.sent.stream()
                .filter(e -> e.getSubject().contains("from alice")).findFirst().orElseThrow();
        assertEquals("[AKHQ] Reminder: access request from alice pending 4 days", toOwner.getSubject());
        assertEquals("owner-a@example.com", toOwner.getTo().iterator().next().getEmail());
        assertTrue(text(toOwner).contains(PUBLIC_URL + "/ui/prod/access-management?request=" + overdue.getId()));
        assertTrue(text(toOwner).contains("reminder"), text(toOwner));

        Email toAdmin = mailbox.sent.stream()
                .filter(e -> e.getSubject().contains("from carol")).findFirst().orElseThrow();
        assertEquals("admin@example.com", toAdmin.getTo().iterator().next().getEmail());
        assertTrue(text(toAdmin).contains("/ui/" + KafkaTestCluster.CLUSTER_ID + "/access-management?request=" + noOwner.getId()));

        assertEquals(now, repository.findById(overdue.getId()).orElseThrow().getLastRemindedAt());
        assertNull(repository.findById(fresh.getId()).orElseThrow().getLastRemindedAt());

        // same moment again: nothing is due
        mailbox.sent.clear();
        assertEquals(0, job.run(now));

        // three days later: only the still-pending ones are reminded again
        AccessRequestEntity decided = repository.findById(noOwner.getId()).orElseThrow();
        decided.setStatus(AccessRequestEntity.STATUS_REJECTED);
        decided.setResolvedBy("admin");
        decided.setResolvedAt(now);
        repository.update(decided);

        Instant later = now.plus(Duration.ofDays(3));
        int sentLater = job.run(later);
        assertTrue(sentLater >= 2, "alice (reminded 3d ago) and bob (created 3d1h ago), got " + sentLater);
        assertTrue(mailbox.sent.stream().anyMatch(e -> e.getSubject().contains("from alice")));
        assertTrue(mailbox.sent.stream().anyMatch(e -> e.getSubject().contains("from bob")));
        assertTrue(mailbox.sent.stream().noneMatch(e -> e.getSubject().contains("from carol")), "rejected request is not reminded");
    }

    @Test
    @Order(4)
    void claimIsAtomic() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        AccessRequestEntity overdue = pendingRequest("dave", "team-a.x", now.minus(Duration.ofDays(5)), "prod");
        Instant threshold = now.minus(Duration.ofDays(3));

        assertEquals(1, repository.claimReminder(overdue.getId(), threshold, now));
        assertEquals(0, repository.claimReminder(overdue.getId(), threshold, now), "second replica loses");
    }

    @Test
    @Order(5)
    void zeroIntervalDisablesReminders() {
        Instant now = Instant.now();
        pendingRequest("erin", "team-a.y", now.minus(Duration.ofDays(30)), "prod");
        Duration saved = properties.getNotifications().getReminderInterval();
        try {
            properties.getNotifications().setReminderInterval(Duration.ZERO);
            assertEquals(0, job.run(now));
            assertTrue(mailbox.sent.isEmpty());
        } finally {
            properties.getNotifications().setReminderInterval(saved);
        }
    }
}
