package org.akhq.security.claim;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.akhq.configs.accessmanagement.AccessManagementProperties;
import org.akhq.configs.security.Group;
import org.akhq.models.accessmanagement.TopicAccessEntity;
import org.akhq.models.security.ClaimProvider;
import org.akhq.models.security.ClaimRequest;
import org.akhq.models.security.ClaimResponse;
import org.akhq.repositories.accessmanagement.TopicAccessRepository;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Singleton
@Primary
@Requires(property = "akhq.access-management.enabled", value = "true")
public class DatabaseClaimProvider implements ClaimProvider {

    @Inject
    private LocalSecurityClaimProvider localSecurityClaimProvider;

    @Inject
    private TopicAccessRepository topicAccessRepository;

    @Inject
    private AccessManagementProperties properties;

    @Override
    public ClaimResponse generateClaim(ClaimRequest request) {
        ClaimResponse localResponse = localSecurityClaimProvider.generateClaim(request);

        List<TopicAccessEntity> accesses = topicAccessRepository.findByUsername(request.getUsername());
        if (accesses.isEmpty()) {
            return localResponse;
        }

        Map<String, List<Group>> groups = new HashMap<>(localResponse.getGroups());

        for (TopicAccessEntity access : accesses) {
            String akhqRole = resolveAkhqRole(access.getRole());
            if (akhqRole == null) {
                String target = access.getPrefix() != null ? access.getPrefix() : access.getTopicName();
                log.warn("No akhq-role mapping for role '{}', skipping access for user '{}' on '{}'",
                        access.getRole(), access.getUsername(), target);
                continue;
            }

            String topicPattern;
            String groupKey;
            if (access.getPrefix() != null) {
                topicPattern = access.getPrefix();
                groupKey = "db-access-" + access.getUsername() + "-prefix-" + access.getPrefix() + "-" + access.getRole();
            } else {
                topicPattern = Pattern.quote(access.getTopicName());
                groupKey = "db-access-" + access.getUsername() + "-" + access.getTopicName() + "-" + access.getRole();
            }

            Group group = new Group();
            group.setRole(akhqRole);
            group.setPatterns(List.of(topicPattern));
            group.setClusters(List.of(".*"));

            groups.put(groupKey, List.of(group));
        }

        return ClaimResponse.builder().groups(groups).build();
    }

    private String resolveAkhqRole(String requestRole) {
        return properties.getRequestableRoles().stream()
                .filter(r -> r.getName().equals(requestRole))
                .map(AccessManagementProperties.RequestableRole::getAkhqRole)
                .findFirst()
                .orElse(null);
    }
}
