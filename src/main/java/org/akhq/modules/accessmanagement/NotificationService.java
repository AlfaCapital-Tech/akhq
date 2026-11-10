package org.akhq.modules.accessmanagement;

import io.micronaut.email.Email;
import io.micronaut.email.EmailSender;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.akhq.configs.Connection;
import org.akhq.configs.accessmanagement.AccessManagementProperties;
import org.akhq.configs.accessmanagement.AccessManagementProperties.Owner;
import org.akhq.configs.accessmanagement.AccessManagementProperties.PrefixOwner;
import org.akhq.models.accessmanagement.AccessRequestEntity;
import org.akhq.models.accessmanagement.TopicAccessEntity;

import com.google.common.html.HtmlEscapers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class NotificationService {

    @Inject
    private AccessManagementProperties properties;

    @Inject
    private Optional<EmailSender> emailSender;

    @Inject
    private List<Connection> connections;

    private static final DateTimeFormatter CREATED_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    public void notifyNewRequest(AccessRequestEntity request) {
        String target = request.getPrefix() != null ? request.getPrefix() : request.getTopicName();
        List<String> ownerEmails = request.getPrefix() != null
                ? findOwnerEmailsByPrefix(request.getPrefix())
                : findOwnerEmails(request.getTopicName());
        if (ownerEmails.isEmpty()) {
            ownerEmails = getSuperAdminEmails();
        }

        String targetType = request.getPrefix() != null ? "prefix" : "topic";
        String logMessage = String.format(
                "New access request: user '%s' requests '%s' access to %s '%s'. Reason: %s. Notify: %s",
                request.getUsername(), request.getRole(), targetType, target,
                request.getReason() != null ? request.getReason() : "-",
                String.join(", ", ownerEmails)
        );
        log.info("ACCESS-MANAGEMENT: {}", logMessage);

        String subject = subjectPrefix() + " New access request from " + request.getUsername();
        sendEmail(ownerEmails, subject, htmlBody(request, null), textBody(request, null));
    }

    /**
     * Repeats the new-request email to the current owners while the request stays pending.
     * Recipients are resolved again: owners may have changed in the configuration since the first email.
     */
    public void notifyReminder(AccessRequestEntity request, Instant now) {
        List<String> ownerEmails = recipients(request);
        long days = Math.max(0, Duration.between(request.getCreatedAt(), now).toDays());
        String target = request.getPrefix() != null ? request.getPrefix() : request.getTopicName();
        log.info("ACCESS-MANAGEMENT: Reminder: request {} of user '{}' for '{}' pending {} days. Notify: {}",
                request.getId(), request.getUsername(), target, days, String.join(", ", ownerEmails));

        String subject = subjectPrefix() + " Reminder: access request from " + request.getUsername()
                + " pending " + days + " days";
        sendEmail(ownerEmails, subject, htmlBody(request, now), textBody(request, now));
    }

    private List<String> recipients(AccessRequestEntity request) {
        List<String> ownerEmails = request.getPrefix() != null
                ? findOwnerEmailsByPrefix(request.getPrefix())
                : findOwnerEmails(request.getTopicName());
        return ownerEmails.isEmpty() ? getSuperAdminEmails() : ownerEmails;
    }

    private String textBody(AccessRequestEntity request, Instant reminderAt) {
        String target = request.getPrefix() != null ? request.getPrefix() : request.getTopicName();
        String targetType = request.getPrefix() != null ? "prefix" : "topic";
        String reason = request.getReason() != null ? request.getReason() : "-";
        StringBuilder sb = new StringBuilder();
        if (reminderAt != null) {
            sb.append("This is a reminder: the request below is still waiting for a decision.\n\n");
        }
        sb.append(String.format("User '%s' requests '%s' access to %s '%s'.\n\nReason: %s\n",
                request.getUsername(), request.getRole(), targetType, target, reason));
        if (reminderAt != null) {
            sb.append("Created: ").append(CREATED_FORMAT.format(request.getCreatedAt())).append("\n");
        }
        String url = requestUrl(request);
        if (url != null) {
            sb.append("\nReview: ").append(url);
        }
        return sb.toString();
    }

    private String htmlBody(AccessRequestEntity request, Instant reminderAt) {
        var esc = HtmlEscapers.htmlEscaper();
        String target = request.getPrefix() != null ? request.getPrefix() : request.getTopicName();
        String targetType = request.getPrefix() != null ? "prefix" : "topic";
        String reason = request.getReason() != null ? request.getReason() : "-";
        StringBuilder sb = new StringBuilder();
        if (reminderAt != null) {
            sb.append("<p><i>This is a reminder: the request below is still waiting for a decision.</i></p>");
        }
        sb.append(String.format("<p>User <b>%s</b> requests <b>%s</b> access to %s <b>%s</b>.</p><p>Reason: %s</p>",
                esc.escape(request.getUsername()), esc.escape(request.getRole()),
                esc.escape(targetType), esc.escape(target), esc.escape(reason)));
        if (reminderAt != null) {
            sb.append("<p>Created: ").append(CREATED_FORMAT.format(request.getCreatedAt())).append("</p>");
        }
        String url = requestUrl(request);
        if (url != null) {
            sb.append("<p><a href=\"").append(esc.escape(url)).append("\">Review in AKHQ</a></p>");
        }
        return sb.toString();
    }

    /**
     * Deep link to the request on the Access Management page of the cluster it was created in.
     * Rows created before the cluster column exist fall back to the first configured cluster.
     * Returns null when base-url is not configured.
     */
    String requestUrl(AccessRequestEntity request) {
        String baseUrl = properties.getNotifications().getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            return null;
        }
        String cluster = request.getCluster();
        if ((cluster == null || cluster.isEmpty()) && !connections.isEmpty()) {
            cluster = connections.get(0).getName();
        }
        if (cluster == null || cluster.isEmpty()) {
            return baseUrl + "/ui";
        }
        return baseUrl + "/ui/" + URLEncoder.encode(cluster, StandardCharsets.UTF_8)
                + "/access-management?request=" + request.getId();
    }

    public void notifyApproved(AccessRequestEntity request) {
        String target = request.getPrefix() != null ? request.getPrefix() : request.getTopicName();
        log.info("ACCESS-MANAGEMENT: Access approved: user '{}' got '{}' access to '{}', approved by '{}'",
                request.getUsername(), request.getRole(), target, request.getResolvedBy());
    }

    public void notifyRejected(AccessRequestEntity request) {
        String target = request.getPrefix() != null ? request.getPrefix() : request.getTopicName();
        log.info("ACCESS-MANAGEMENT: Access rejected: user '{}' request '{}' to '{}' rejected by '{}'. Reason: {}",
                request.getUsername(), request.getRole(), target, request.getResolvedBy(),
                request.getRejectReason() != null ? request.getRejectReason() : "-");
    }

    public void notifyRevoked(TopicAccessEntity access, String revokedBy) {
        String target = access.getPrefix() != null ? access.getPrefix() : access.getTopicName();
        log.info("ACCESS-MANAGEMENT: Access revoked: user '{}' lost '{}' access to '{}', revoked by '{}'",
                access.getUsername(), access.getRole(), target, revokedBy);
    }

    private void sendEmail(List<String> recipients, String subject, String htmlBody, String textBody) {
        if (!Boolean.TRUE.equals(properties.getNotifications().getEnabled()) || recipients.isEmpty()) {
            return;
        }
        if (emailSender.isEmpty()) {
            log.warn("ACCESS-MANAGEMENT: notifications enabled but EmailSender bean not found, check SMTP config");
            return;
        }
        for (String recipient : recipients) {
            try {
                emailSender.get().send(Email.builder()
                        .to(recipient)
                        .subject(subject)
                        .body(htmlBody, textBody)
                );
                log.debug("ACCESS-MANAGEMENT: email sent to {}", recipient);
            } catch (Exception e) {
                log.error("ACCESS-MANAGEMENT: failed to send email to {}: {}", recipient, e.getMessage(), e);
            }
        }
    }

    private String subjectPrefix() {
        String prefix = properties.getNotifications().getSubjectPrefix();
        return prefix != null ? prefix : "[AKHQ]";
    }

    private List<String> findOwnerEmails(String topicName) {
        if (topicName == null) return List.of();
        List<String> emails = new ArrayList<>();
        for (PrefixOwner po : properties.getPrefixOwners()) {
            if (Pattern.matches(po.getPrefix(), topicName)) {
                for (Owner o : po.getOwners()) {
                    if (o.getEmail() != null) {
                        emails.add(o.getEmail());
                    }
                }
            }
        }
        return emails;
    }

    private List<String> findOwnerEmailsByPrefix(String prefix) {
        List<String> emails = new ArrayList<>();
        for (PrefixOwner po : properties.getPrefixOwners()) {
            if (po.getPrefix().equals(prefix)) {
                for (Owner o : po.getOwners()) {
                    if (o.getEmail() != null) {
                        emails.add(o.getEmail());
                    }
                }
            }
        }
        return emails;
    }

    private List<String> getSuperAdminEmails() {
        return properties.getSuperAdmins().stream()
                .map(AccessManagementProperties.SuperAdmin::getEmail)
                .filter(Objects::nonNull)
                .toList();
    }
}
