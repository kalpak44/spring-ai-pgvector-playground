package online.pavelusanli.repo;

import online.pavelusanli.model.entity.BoardNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BoardNotificationRepository extends JpaRepository<BoardNotification, Long> {

    @Query(value = "SELECT n FROM BoardNotification n WHERE n.userId = :userId ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM BoardNotification n WHERE n.userId = :userId")
    Page<BoardNotification> findByUserId(@Param("userId") Long userId, Pageable pageable);

    List<BoardNotification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadFalse(Long userId);

    @Transactional
    @Modifying
    @Query("UPDATE BoardNotification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    void markAllReadByUserId(@Param("userId") Long userId);

    @Transactional
    @Modifying
    @Query("UPDATE BoardNotification n SET n.read = true WHERE n.id = :id AND n.userId = :userId")
    void markReadByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}