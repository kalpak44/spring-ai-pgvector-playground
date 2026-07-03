package online.pavelusanli.model.entity;

import jakarta.persistence.*;
import lombok.*;
import online.pavelusanli.model.common.NotificationEntityType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "board_notification")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 32)
    private NotificationEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(columnDefinition = "JSONB")
    private String payload;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}