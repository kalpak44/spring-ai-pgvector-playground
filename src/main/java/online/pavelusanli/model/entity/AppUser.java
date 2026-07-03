package online.pavelusanli.model.entity;

import jakarta.persistence.*;
import lombok.*;
import online.pavelusanli.model.common.UserRole;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", length = 64)
    private String firstName;

    @Column(name = "last_name", length = 64)
    private String lastName;

    @Column(unique = true, nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "invite_token", unique = true)
    private String inviteToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(nullable = false, length = 8)
    @Builder.Default
    private String language = "en";

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = "Europe/Sofia";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}