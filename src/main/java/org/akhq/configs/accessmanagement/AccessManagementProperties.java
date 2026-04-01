package org.akhq.configs.accessmanagement;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties("akhq.access-management")
public class AccessManagementProperties {

    Boolean enabled = false;

    List<SuperAdmin> superAdmins = new ArrayList<>();

    List<PrefixOwner> prefixOwners = new ArrayList<>();

    List<RequestableRole> requestableRoles = new ArrayList<>();

    NotificationProperties notifications = new NotificationProperties();

    @Data
    public static class SuperAdmin {
        String username;
        String email;
    }

    @Data
    public static class PrefixOwner {
        String prefix;
        List<Owner> owners = new ArrayList<>();
    }

    @Data
    public static class Owner {
        String username;
        String email;
    }

    @Data
    public static class RequestableRole {
        String name;
        String label;
        String akhqRole;
    }

    @Data
    @ConfigurationProperties("notifications")
    public static class NotificationProperties {
        Boolean enabled = false;
        String subjectPrefix = "[AKHQ]";
        String baseUrl;
    }
}
