package online.pavelusanli.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_google_tokens")
public class UserGoogleToken {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expiry")
    private LocalDateTime tokenExpiry;

    @Column(name = "google_email")
    private String googleEmail;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "granted_scopes", columnDefinition = "TEXT")
    private String grantedScopes;
}