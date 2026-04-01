package org.akhq.modules.accessmanagement;

import io.micronaut.email.Email;
import io.micronaut.email.EmailSender;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.akhq.configs.accessmanagement.AccessManagementProperties;
import org.akhq.configs.accessmanagement.AccessManagementProperties.Owner;
import org.akhq.configs.accessmanagement.AccessManagementProperties.PrefixOwner;
import org.akhq.models.accessmanagement.AccessRequestEntity;
import org.akhq.models.accessmanagement.TopicAccessEntity;

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
        String reason = request.getReason() != null ? request.getReason() : "-";
        String textBody = String.format(
                "User '%s' requests '%s' access to %s '%s'.\n\nReason: %s\n\n%s",
                request.getUsername(), request.getRole(), targetType, target, reason,
                accessManagementUrl()
        );
        String htmlBody = String.format(
                "<p>User <b>%s</b> requests <b>%s</b> access to %s <b>%s</b>.</p>"
                + "<p>Reason: %s</p>"
                + "%s",
                request.getUsername(), request.getRole(), targetType, target, reason,
                accessManagementLink()
        );
        sendEmail(ownerEmails, subject, htmlBody, textBody);
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

    private String accessManagementUrl() {
        String baseUrl = properties.getNotifications().getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            return "";
        }
        return "Review: " + baseUrl + "/ui";
    }

    private String accessManagementLink() {
        String baseUrl = properties.getNotifications().getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            return "";
        }
        String url = baseUrl + "/ui";
        return "<p><a href=\"" + url + "\">Review in AKHQ</a></p>";
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
