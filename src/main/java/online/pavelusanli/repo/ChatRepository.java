package online.pavelusanli.repo;

import online.pavelusanli.model.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {
    List<Chat> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Chat> findByIdAndUserId(Long id, Long userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM Chat c WHERE c.id = :id AND c.userId = :userId")
    void deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}