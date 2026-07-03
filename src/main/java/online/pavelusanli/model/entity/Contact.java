package online.pavelusanli.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contact")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;
    private String phone;
    private String email;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String updatedBy;

    @OneToMany(mappedBy = "contact", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<ContactNote> notes = new ArrayList<>();

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public String getDisplayName() {
        String fn = firstName != null ? firstName.trim() : "";
        String ln = lastName  != null ? lastName.trim()  : "";
        String full = (fn + " " + ln).trim();
        return full.isEmpty() ? "(no name)" : full;
    }
}