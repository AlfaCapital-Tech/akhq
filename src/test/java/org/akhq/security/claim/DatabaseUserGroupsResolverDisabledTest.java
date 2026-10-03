package org.akhq.security.claim;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import org.akhq.AbstractTest;
import org.akhq.security.authentication.UserGroupsResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DatabaseUserGroupsResolverDisabledTest extends AbstractTest {

    @Inject
    ApplicationContext applicationContext;

    @Inject
    UserGroupsResolver userGroupsResolver;

    @Test
    void upstreamResolverIsUsedWhenAccessManagementDisabled() {
        assertEquals(UserGroupsResolver.class, userGroupsResolver.getClass());
        assertFalse(applicationContext.containsBean(DatabaseClaimProvider.class));
    }
}
