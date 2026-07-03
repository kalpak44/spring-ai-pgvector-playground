package online.pavelusanli.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "card_assignment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @PrePersist
    void prePersist() {
        assignedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
