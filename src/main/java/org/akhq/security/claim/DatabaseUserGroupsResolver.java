package org.akhq.security.claim;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Singleton;
import org.akhq.configs.security.Group;
import org.akhq.security.authentication.UserGroupsResolver;
import org.akhq.security.authentication.mcp.McpOauthAuthentication;
import org.akhq.security.authentication.mcp.McpOauthIdentityResolver;
import org.akhq.security.rule.AKHQSecurityRule;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies access-management grants on every request, so grants and revocations take effect without re-login:
 * {@code db-access-*} groups baked into the AKHQ token are dropped and replaced with fresh ones from the DB.
 * MCP OAuth authentications go through {@link DatabaseClaimProvider}, which already adds the fresh groups.
 */
@Singleton
@Replaces(UserGroupsResolver.class)
@Requires(property = "akhq.access-management.enabled", value = "true")
public class DatabaseUserGroupsResolver extends UserGroupsResolver {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DatabaseClaimProvider claimProvider;

    public DatabaseUserGroupsResolver(DatabaseClaimProvider claimProvider, McpOauthIdentityResolver identityResolver) {
        super(claimProvider, identityResolver);
        this.claimProvider = claimProvider;
    }

    @Override
    public List<Group> resolve(Authentication authentication) {
        if (authentication instanceof McpOauthAuthentication) {
            return super.resolve(authentication);
        }

        Map<String, List<Group>> groups = new HashMap<>(AKHQSecurityRule.unrollGroups(authentication, claimProvider));
        groups.keySet().removeIf(key -> key.startsWith(DatabaseClaimProvider.DYNAMIC_GROUP_PREFIX));
        groups.putAll(claimProvider.resolveDynamicGroups(authentication.getName()));

        return groups.values().stream()
            .flatMap(Collection::stream)
            .map(group -> MAPPER.convertValue(group, Group.class))
            .toList();
    }
}
