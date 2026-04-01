package org.akhq.modules.accessmanagement;

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
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class NotificationService {

    @Inject
    private AccessManagementProperties properties;

    public void notifyNewRequest(AccessRequestEntity request) {
        String target = request.getPrefix() != null ? request.getPrefix() : request.getTopicName();
        List<String> ownerEmails = request.getPrefix() != null
                ? findOwnerEmailsByPrefix(request.getPrefix())
                : findOwnerEmails(request.getTopicName());
        if (ownerEmails.isEmpty()) {
            ownerEmails = getSuperAdminEmails();
        }

        String targetType = request.getPrefix() != null ? "prefix" : "topic";
        String message = String.format(
                "New access request: user '%s' requests '%s' access to %s '%s'. Reason: %s. Notify: %s",
                request.getUsername(), request.getRole(), targetType, target,
                request.getReason() != null ? request.getReason() : "-",
                String.join(", ", ownerEmails)
        );

        log.info("ACCESS-MANAGEMENT: {}", message);
    }

    public void notifyApproved(AccessRequestEntity request) {
        String target = request.getPrefix() != null ? request.getPrefix() : request.getTopicName();
        String message = String.format(
                "Access approved: user '%s' got '%s' access to '%s', approved by '%s'",
                request.getUsername(), request.getRole(), target, request.getResolvedBy()
        );

        log.info("ACCESS-MANAGEMENT: {}", message);
    }

    public void notifyRejected(AccessRequestEntity request) {
        String target = request.getPrefix() != null ? request.getPrefix() : request.getTopicName();
        String message = String.format(
                "Access rejected: user '%s' request '%s' to '%s' rejected by '%s'. Reason: %s",
                request.getUsername(), request.getRole(), target, request.getResolvedBy(),
                request.getRejectReason() != null ? request.getRejectReason() : "-"
        );

        log.info("ACCESS-MANAGEMENT: {}", message);
    }

    public void notifyRevoked(TopicAccessEntity access, String revokedBy) {
        String target = access.getPrefix() != null ? access.getPrefix() : access.getTopicName();
        String message = String.format(
                "Access revoked: user '%s' lost '%s' access to '%s', revoked by '%s'",
                access.getUsername(), access.getRole(), target, revokedBy
        );

        log.info("ACCESS-MANAGEMENT: {}", message);
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
                .filter(e -> e != null)
                .toList();
    }
}
