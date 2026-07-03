package online.pavelusanli.repo;

import online.pavelusanli.model.entity.UserGoogleToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGoogleTokenRepository extends JpaRepository<UserGoogleToken, Long> {
}