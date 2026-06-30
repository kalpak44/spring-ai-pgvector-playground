package online.pavelusanli.repo;

import online.pavelusanli.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByInviteToken(String inviteToken);
    boolean existsByUsername(String username);
}