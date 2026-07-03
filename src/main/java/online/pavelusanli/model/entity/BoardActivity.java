package online.pavelusanli.model.entity;

import jakarta.persistence.*;
import lombok.*;
import online.pavelusanli.model.common.ActivityEntityType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "board_activity")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 32)
    private ActivityEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(columnDefinition = "JSONB")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}