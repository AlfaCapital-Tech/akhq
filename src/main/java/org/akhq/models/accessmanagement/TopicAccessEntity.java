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
@Table(name = "topic_access")
public class TopicAccessEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    @Column(nullable = false)
    private String role;

    @Column(name = "granted_by", nullable = false)
    private String grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "request_id")
    private UUID requestId;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }
}
