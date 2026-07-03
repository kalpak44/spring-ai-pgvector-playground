package online.pavelusanli.repo;

import online.pavelusanli.model.common.ActivityEntityType;
import online.pavelusanli.model.entity.BoardActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BoardActivityRepository extends JpaRepository<BoardActivity, Long> {

    @Query(value = "SELECT a FROM BoardActivity a WHERE a.boardId = :boardId ORDER BY a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM BoardActivity a WHERE a.boardId = :boardId")
    Page<BoardActivity> findByBoardId(@Param("boardId") Long boardId, Pageable pageable);

    @Query(value = "SELECT a FROM BoardActivity a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM BoardActivity a WHERE a.entityType = :entityType AND a.entityId = :entityId")
    Page<BoardActivity> findByEntityTypeAndEntityId(
            @Param("entityType") ActivityEntityType entityType,
            @Param("entityId") Long entityId,
            Pageable pageable);

    @Transactional
    @Modifying
    @Query("DELETE FROM BoardActivity a WHERE a.boardId = :boardId")
    void deleteByBoardId(@Param("boardId") Long boardId);
}