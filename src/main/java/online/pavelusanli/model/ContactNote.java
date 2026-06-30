package online.pavelusanli.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "contact_note")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ContactNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @Column(nullable = false, length = 64)
    private String authorUsername;

    private String authorDisplayName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}