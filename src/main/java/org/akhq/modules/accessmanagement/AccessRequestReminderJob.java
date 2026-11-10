package org.akhq.modules.accessmanagement;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.akhq.configs.accessmanagement.AccessManagementProperties;
import org.akhq.models.accessmanagement.AccessRequestEntity;
import org.akhq.repositories.accessmanagement.AccessRequestRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Periodically re-sends the new-request email for requests that stay pending longer than
 * {@code akhq.access-management.notifications.reminder-interval}.
 * <p>
 * Safe with several replicas: a reminder is sent only after this instance moved the
 * {@code last_reminded_at} marker with a conditional UPDATE, so the database acts as the lock.
 */
@Slf4j
@Singleton
@Requires(property = "akhq.access-management.enabled", value = "true")
public class AccessRequestReminderJob {

    @Inject
    private AccessManagementProperties properties;

    @Inject
    private AccessRequestRepository accessRequestRepository;

    @Inject
    private NotificationService notificationService;

    @Scheduled(fixedDelay = "${akhq.access-management.notifications.reminder-check-interval:15m}",
               initialDelay = "${akhq.access-management.notifications.reminder-initial-delay:1m}")
    void tick() {
        try {
            run(Instant.now());
        } catch (Exception e) {
            log.error("ACCESS-MANAGEMENT: reminder job failed: {}", e.getMessage(), e);
        }
    }

    /** Runs one pass and returns the number of reminders sent. */
    public int run(Instant now) {
        Duration interval = properties.getNotifications().getReminderInterval();
        if (!Boolean.TRUE.equals(properties.getNotifications().getEnabled())
                || interval == null || interval.isZero() || interval.isNegative()) {
            return 0;
        }
        Instant threshold = now.minus(interval);
        List<AccessRequestEntity> due = accessRequestRepository.findDueForReminder(threshold);
        int sent = 0;
        for (AccessRequestEntity request : due) {
            if (accessRequestRepository.claimReminder(request.getId(), threshold, now) != 1) {
                log.debug("ACCESS-MANAGEMENT: reminder for {} skipped: resolved or claimed by another replica",
                        request.getId());
                continue;
            }
            request.setLastRemindedAt(now);
            notificationService.notifyReminder(request, now);
            sent++;
        }
        if (sent > 0) {
            log.info("ACCESS-MANAGEMENT: {} reminder(s) sent", sent);
        }
        return sent;
    }
}
