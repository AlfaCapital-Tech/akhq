package org.akhq.controllers;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.utils.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.akhq.configs.accessmanagement.AccessManagementProperties;
import org.akhq.configs.accessmanagement.AccessManagementProperties.Owner;
import org.akhq.configs.accessmanagement.AccessManagementProperties.PrefixOwner;
import org.akhq.models.accessmanagement.AccessRequestEntity;
import org.akhq.models.accessmanagement.TopicAccessEntity;
import org.akhq.modules.accessmanagement.NotificationService;
import org.akhq.repositories.accessmanagement.AccessRequestRepository;
import org.akhq.repositories.accessmanagement.TopicAccessRepository;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Secured(SecurityRule.IS_ANONYMOUS)
@Controller("/api/{cluster}/access-management")
@ExecuteOn(TaskExecutors.IO)
public class AccessManagementController {

    @Inject
    private AccessManagementProperties properties;

    @Inject
    private AccessRequestRepository accessRequestRepository;

    @Inject
    private TopicAccessRepository topicAccessRepository;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private NotificationService notificationService;

    // --- User endpoints ---

    @Get("/prefixes")
    @Operation(tags = {"Access Management"}, summary = "Get available prefixes for access requests")
    public HttpResponse<?> availablePrefixes(String cluster) {
        if (!isEnabled()) {
            return notFoundResponse("Access management is disabled");
        }
        List<PrefixInfo> prefixes = properties.getPrefixOwners().stream()
                .map(po -> new PrefixInfo(
                        po.getPrefix(),
                        po.getOwners().stream()
                                .map(o -> new OwnerInfo(o.getUsername(), o.getEmail()))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());
        return HttpResponse.ok(prefixes);
    }

    @Post("/request")
    @Operation(tags = {"Access Management"}, summary = "Create access request")
    public HttpResponse<?> createRequest(String cluster, @Body CreateRequestBody body) {
        if (!isEnabled()) {
            return notFoundResponse("Access management is disabled");
        }
        String username = getCurrentUsername();

        boolean isPrefix = body.getPrefix() != null && !body.getPrefix().isEmpty();
        boolean isTopic = body.getTopicName() != null && !body.getTopicName().isEmpty();

        if (!isPrefix && !isTopic) {
            return HttpResponse.badRequest(new JsonError("Either topicName or prefix must be provided"));
        }

        // Check for duplicate pending request
        List<AccessRequestEntity> existing;
        if (isPrefix) {
            existing = accessRequestRepository
                    .findByUsernameAndPrefixAndStatus(username, body.getPrefix(), AccessRequestEntity.STATUS_PENDING);
        } else {
            existing = accessRequestRepository
                    .findByUsernameAndTopicNameAndStatus(username, body.getTopicName(), AccessRequestEntity.STATUS_PENDING);
        }
        if (!existing.isEmpty()) {
            return HttpResponse.status(HttpStatus.CONFLICT)
                    .body(new JsonError("Pending request already exists"));
        }

        String roleError = validateRole(body.getRole());
        if (roleError != null) {
            return HttpResponse.badRequest(new JsonError(roleError));
        }

        AccessRequestEntity entity = new AccessRequestEntity();
        entity.setUsername(username);
        entity.setRole(body.getRole());
        entity.setStatus(AccessRequestEntity.STATUS_PENDING);
        entity.setReason(body.getReason());

        if (isPrefix) {
            entity.setPrefix(body.getPrefix());
        } else {
            entity.setTopicName(body.getTopicName());
        }

        AccessRequestEntity saved = accessRequestRepository.save(entity);
        notificationService.notifyNewRequest(saved);
        return HttpResponse.ok(saved);
    }

    @Get("/requests/my")
    @Operation(tags = {"Access Management"}, summary = "Get my access requests")
    public HttpResponse<?> myRequests(String cluster) {
        if (!isEnabled()) {
            return notFoundResponse("Access management is disabled");
        }
        return HttpResponse.ok(accessRequestRepository.findByUsername(getCurrentUsername()));
    }

    @Get("/topic/{topicName}/owners")
    @Operation(tags = {"Access Management"}, summary = "Get topic owners")
    public HttpResponse<?> getTopicOwners(String cluster, String topicName) {
        if (!isEnabled()) {
            return notFoundResponse("Access management is disabled");
        }
        List<OwnerInfo> owners = findOwnersForTopic(topicName);
        return HttpResponse.ok(new TopicOwnersResponse(topicName, owners));
    }

    @Get("/topic/{topicName}/my-access")
    @Operation(tags = {"Access Management"}, summary = "Get my access to topic")
    public HttpResponse<?> getMyAccess(String cluster, String topicName) {
        if (!isEnabled()) {
            return notFoundResponse("Access management is disabled");
        }
        String username = getCurrentUsername();
        List<TopicAccessEntity> accesses = topicAccessRepository.findByUsername(username).stream()
                .filter(a -> topicName.equals(a.getTopicName()))
                .collect(Collectors.toList());
        return HttpResponse.ok(accesses);
    }

    // --- Owner/SuperAdmin endpoints ---

    @Get("/requests/pending/count")
    @Operation(tags = {"Access Management"}, summary = "Get count of pending requests for owned topics")
    public HttpResponse<?> pendingRequestsCount(String cluster) {
        if (!isEnabled()) {
            return notFoundResponse("Access management is disabled");
        }
        String username = getCurrentUsername();
        List<AccessRequestEntity> all = accessRequestRepository.findByStatus(AccessRequestEntity.STATUS_PENDING);
        int count = filterByOwnership(all, username).size();
        return HttpResponse.ok(Map.of("count", count));
    }

    @Get("/requests/pending")
    @Operation(tags = {"Access Management"}, summary = "Get pending requests for owned topics")
    public HttpResponse<?> pendingRequests(String cluster) {
        if (!isEnabled()) {
            return notFoundResponse("Access management is disabled");
        }
        String username = getCurrentUsername();
        List<AccessRequestEntity> all = accessRequestRepository.findByStatus(AccessRequestEntity.STATUS_PENDING);
        return HttpResponse.ok(filterByOwnership(all, username));
    }

    @Get("/requests/all")
    @Operation(tags = {"Access Management"}, summary = "Get all requests for owned topics")
    public HttpResponse<?> allRequests(String cluster) {
        if (!isEnabled()) {
            return notFoundResponse("Access management is disabled");
        }
        String username = getCurrentUsername();
        List<AccessRequestEntity> all = accessRequestRepository.findAll();
        return HttpResponse.ok(filterByOwnership(all, username));
    }

    @Put("/requests/{id}/approve")
    @Operation(tags = {"Access Management"}, summary = "Approve access request")
    public HttpResponse<?> approveRequest(String cluster, UUID id) {
        if (!isEnabled()) {
            return notFoundResponse("Access management is disabled");
        }
        String username = getCurrentUsername();

        Optional<AccessRequestEntity> optRequest = accessRequestRepository.findById(id);
        if (optRequest.isEmpty()) {
            return notFoundResponse("Request not found");
        }
        AccessRequestEntity request = optRequest.get();

        String requestTarget = request.getPrefix() != null ? request.getPrefix() : request.getTopicName();
        if (!isOwnerOfTarget(username, request)) {
            return HttpResponse.status(HttpStatus.FORBIDDEN)
                    .body(new JsonError("You are not an owner of: " + requestTarget));
        }

        if (!AccessRequestEntity.STATUS_PENDING.equals(request.getStatus())) {
            return HttpResponse.badRequest(new JsonError("Request is not in PENDING status"));
        }

        request.setStatus(AccessRequestEntity.STATUS_APPROVED);
        request.setResolvedBy(username);
        request.setResolvedAt(Instant.now());
        accessRequestRepository.update(request);

        TopicAccessEntity access = new TopicAccessEntity();
        access.setUsername(request.getUsername());
        access.setTopicName(request.getTopicName());
        access.setPrefix(request.getPrefix());
        access.setRole(request.getRole());
        access.setGrantedBy(username);
        access.setRequestId(request.getId());

        Optional<TopicAccessEntity> existingAccess;
        if (request.getPrefix() != null) {
            existingAccess = topicAccessRepository
                    .findByUsernameAndPrefixAndRole(request.getUsername(), request.getPrefix(), request.getRole());
        } else {
            existingAccess = topicAccessRepository
                    .findByUsernameAndTopicNameAndRole(request.getUsername(), request.getTopicName(), request.getRole());
        }
        if (existingAccess.isEmpty()) {
            topicAccessRepository.save(access);
        }

        notificationService.notifyApproved(request);
        return HttpResponse.ok(request);
    }

    @Put("/requests/{id}/reject")
    @Operation(tags = {"Access Management"}, summary = "Reject access request")
    public HttpResponse<?> rejectRequest(String cluster, UUID id, @Body RejectBody body) {
        if (!isEnabled()) {
            return notFoundResponse("Access management is disabled");
        }
        String username = getCurrentUsername();

        Optional<AccessRequestEntity> optRequest = accessRequestRepository.findById(id);
        if (optRequest.isEmpty()) {
            return notFoundResponse("Request not found");
        }
        AccessRequestEntity request = optRequest.get();

        if (!isOwnerOfTarget(username, request)) {
            String target = request.getPrefix() != null ? request.getPrefix() : request.getTopicName();
            return HttpResponse.status(HttpStatus.FORBIDDEN)
                    .body(new JsonError("You are not an owner of: " + target));
        }

        if (!AccessRequestEntity.STATUS_PENDING.equals(request.getStatus())) {
            return HttpResponse.badRequest(new JsonError("Request is not in PENDING status"));
        }

        request.setStatus(AccessRequestEntity.STATUS_REJECTED);
        request.setResolvedBy(username);
        request.setResolvedAt(Instant.now());
        request.setRejectReason(body.getRejectReason());
        accessRequestRepository.update(request);

        notificationService.notifyRejected(request);
        return HttpResponse.ok(request);
    }

    @Get("/accesses")
    @Operation(tags = {"Access Management"}, summary = "Get current accesses for owned topics")
    public HttpResponse<?> accesses(String cluster) {
        if (!isEnabled()) {
            return notFoundResponse("Access management is disabled");
        }
        String username = getCurrentUsername();
        List<TopicAccessEntity> all = topicAccessRepository.findAll();
        return HttpResponse.ok(filterAccessesByOwnership(all, username));
    }

    @Delete("/accesses/{id}")
    @Operation(tags = {"Access Management"}, summary = "Revoke access")
    public HttpResponse<?> revokeAccess(String cluster, UUID id) {
        if (!isEnabled()) {
            return notFoundResponse("Access management is disabled");
        }
        String username = getCurrentUsername();

        Optional<TopicAccessEntity> optAccess = topicAccessRepository.findById(id);
        if (optAccess.isEmpty()) {
            return notFoundResponse("Access not found");
        }
        TopicAccessEntity access = optAccess.get();

        String target = access.getPrefix() != null ? access.getPrefix() : access.getTopicName();
        if (!isOwnerOfTarget(username, access.getTopicName(), access.getPrefix())) {
            return HttpResponse.status(HttpStatus.FORBIDDEN)
                    .body(new JsonError("You are not an owner of: " + target));
        }

        topicAccessRepository.delete(access);
        notificationService.notifyRevoked(access, username);
        return HttpResponse.ok();
    }

    // --- Internal helpers ---

    private boolean isEnabled() {
        return Boolean.TRUE.equals(properties.getEnabled());
    }

    private HttpResponse<?> notFoundResponse(String message) {
        return HttpResponse.notFound(new JsonError(message));
    }

    private String getCurrentUsername() {
        if (applicationContext.containsBean(SecurityService.class)) {
            return applicationContext.getBean(SecurityService.class)
                    .getAuthentication()
                    .map(auth -> auth.getName())
                    .orElse("anonymous");
        }
        return "anonymous";
    }

    private String validateRole(String role) {
        boolean valid = properties.getRequestableRoles().stream()
                .anyMatch(r -> r.getName().equals(role));
        if (!valid) {
            return "Invalid role: " + role + ". Available: " +
                    properties.getRequestableRoles().stream()
                            .map(AccessManagementProperties.RequestableRole::getName)
                            .collect(Collectors.joining(", "));
        }
        return null;
    }

    private boolean isSuperAdmin(String username) {
        return properties.getSuperAdmins().stream()
                .anyMatch(sa -> sa.getUsername().equalsIgnoreCase(username));
    }

    private List<String> getOwnedPrefixes(String username) {
        return properties.getPrefixOwners().stream()
                .filter(po -> po.getOwners().stream()
                        .anyMatch(o -> o.getUsername().equalsIgnoreCase(username)))
                .map(PrefixOwner::getPrefix)
                .collect(Collectors.toList());
    }

    private boolean isOwnerOfTarget(String username, AccessRequestEntity request) {
        return isOwnerOfTarget(username, request.getTopicName(), request.getPrefix());
    }

    private boolean isOwnerOfTarget(String username, String topicName, String prefix) {
        if (isSuperAdmin(username)) {
            return true;
        }
        List<String> ownedPrefixes = getOwnedPrefixes(username);
        if (prefix != null) {
            return ownedPrefixes.contains(prefix);
        }
        if (topicName != null) {
            return ownedPrefixes.stream()
                    .anyMatch(p -> Pattern.matches(p, topicName));
        }
        return false;
    }

    private boolean matchesOwnership(String topicName, String prefix, List<String> ownedPrefixes) {
        if (prefix != null) {
            return ownedPrefixes.contains(prefix);
        }
        if (topicName != null) {
            return ownedPrefixes.stream()
                    .anyMatch(p -> Pattern.matches(p, topicName));
        }
        return false;
    }

    private List<AccessRequestEntity> filterByOwnership(List<AccessRequestEntity> requests, String username) {
        if (isSuperAdmin(username)) {
            return requests;
        }
        List<String> prefixes = getOwnedPrefixes(username);
        if (prefixes.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .filter(r -> matchesOwnership(r.getTopicName(), r.getPrefix(), prefixes))
                .collect(Collectors.toList());
    }

    private List<TopicAccessEntity> filterAccessesByOwnership(List<TopicAccessEntity> accesses, String username) {
        if (isSuperAdmin(username)) {
            return accesses;
        }
        List<String> prefixes = getOwnedPrefixes(username);
        if (prefixes.isEmpty()) {
            return List.of();
        }
        return accesses.stream()
                .filter(a -> matchesOwnership(a.getTopicName(), a.getPrefix(), prefixes))
                .collect(Collectors.toList());
    }

    private List<OwnerInfo> findOwnersForTopic(String topicName) {
        List<OwnerInfo> result = new ArrayList<>();
        for (PrefixOwner po : properties.getPrefixOwners()) {
            if (Pattern.matches(po.getPrefix(), topicName)) {
                for (Owner o : po.getOwners()) {
                    result.add(new OwnerInfo(o.getUsername(), o.getEmail()));
                }
            }
        }
        return result;
    }

    // --- DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequestBody {
        private String topicName;
        private String prefix;
        private String role;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectBody {
        private String rejectReason;
    }

    @Getter
    @AllArgsConstructor
    public static class TopicOwnersResponse {
        private String topicName;
        private List<OwnerInfo> owners;
    }

    @Getter
    @AllArgsConstructor
    public static class OwnerInfo {
        private String username;
        private String email;
    }

    @Getter
    @AllArgsConstructor
    public static class PrefixInfo {
        private String prefix;
        private List<OwnerInfo> owners;
    }
}
