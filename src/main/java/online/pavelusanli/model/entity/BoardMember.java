package online.pavelusanli.model.entity;

import jakarta.persistence.*;
import lombok.*;
import online.pavelusanli.model.common.BoardMemberRole;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "board_member")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private BoardMemberRole role = BoardMemberRole.MEMBER;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    void prePersist() {
        joinedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}