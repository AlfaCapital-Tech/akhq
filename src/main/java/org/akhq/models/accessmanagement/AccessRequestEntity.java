package org.akhq.models.accessmanagement;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Serdeable
@Getter
@Setter
@Entity
@Table(name = "access_request")
public class AccessRequestEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String status;

    private String reason;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
