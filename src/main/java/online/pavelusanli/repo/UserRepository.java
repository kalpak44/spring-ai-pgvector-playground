package online.pavelusanli.repo;

import online.pavelusanli.model.common.UserRole;
import online.pavelusanli.model.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByInviteToken(String inviteToken);
    boolean existsByUsername(String username);
    boolean existsByRole(UserRole role);

    @Query(value = "SELECT u FROM AppUser u WHERE u.username != :me AND " +
                   "(:q = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                   "LOWER(COALESCE(u.firstName, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                   "LOWER(COALESCE(u.lastName, '')) LIKE LOWER(CONCAT('%', :q, '%')))",
           countQuery = "SELECT COUNT(u) FROM AppUser u WHERE u.username != :me AND " +
                        "(:q = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                        "LOWER(COALESCE(u.firstName, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                        "LOWER(COALESCE(u.lastName, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<AppUser> search(@Param("me") String currentUsername, @Param("q") String q, Pageable pageable);
}