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
        List<String> ownerEmails = findOwnerEmails(request.getTopicName());
        if (ownerEmails.isEmpty()) {
            ownerEmails = getSuperAdminEmails();
        }

        String message = String.format(
                "New access request: user '%s' requests '%s' access to topic '%s'. Reason: %s. Notify: %s",
                request.getUsername(), request.getRole(), request.getTopicName(),
                request.getReason() != null ? request.getReason() : "-",
                String.join(", ", ownerEmails)
        );

        log.info("ACCESS-MANAGEMENT: {}", message);
    }

    public void notifyApproved(AccessRequestEntity request) {
        String message = String.format(
                "Access approved: user '%s' got '%s' access to topic '%s', approved by '%s'",
                request.getUsername(), request.getRole(), request.getTopicName(), request.getResolvedBy()
        );

        log.info("ACCESS-MANAGEMENT: {}", message);
    }

    public void notifyRejected(AccessRequestEntity request) {
        String message = String.format(
                "Access rejected: user '%s' request '%s' to topic '%s' rejected by '%s'. Reason: %s",
                request.getUsername(), request.getRole(), request.getTopicName(), request.getResolvedBy(),
                request.getRejectReason() != null ? request.getRejectReason() : "-"
        );

        log.info("ACCESS-MANAGEMENT: {}", message);
    }

    public void notifyRevoked(TopicAccessEntity access, String revokedBy) {
        String message = String.format(
                "Access revoked: user '%s' lost '%s' access to topic '%s', revoked by '%s'",
                access.getUsername(), access.getRole(), access.getTopicName(), revokedBy
        );

        log.info("ACCESS-MANAGEMENT: {}", message);
    }

    private List<String> findOwnerEmails(String topicName) {
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

    private List<String> getSuperAdminEmails() {
        return properties.getSuperAdmins().stream()
                .map(AccessManagementProperties.SuperAdmin::getEmail)
                .filter(e -> e != null)
                .toList();
    }
}
