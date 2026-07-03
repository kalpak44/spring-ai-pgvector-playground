package online.pavelusanli.repo;

import online.pavelusanli.model.entity.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByColumnIdOrderByPosition(Long columnId);

    // Cards across all columns of a board
    @Query("SELECT c FROM Card c WHERE c.columnId IN " +
           "(SELECT col.id FROM BoardColumn col WHERE col.boardId = :boardId) " +
           "ORDER BY c.columnId, c.position")
    List<Card> findByBoardId(@Param("boardId") Long boardId);

    @Query("SELECT COALESCE(MAX(c.position), 0) FROM Card c WHERE c.columnId = :columnId")
    int findMaxPositionByColumnId(@Param("columnId") Long columnId);

    @Query(value = "SELECT c FROM Card c WHERE c.columnId IN " +
                   "(SELECT col.id FROM BoardColumn col WHERE col.boardId = :boardId) AND " +
                   "(:q = '' OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%')))",
           countQuery = "SELECT COUNT(c) FROM Card c WHERE c.columnId IN " +
                        "(SELECT col.id FROM BoardColumn col WHERE col.boardId = :boardId) AND " +
                        "(:q = '' OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Card> searchByBoardId(@Param("boardId") Long boardId, @Param("q") String q, Pageable pageable);

    @Transactional
    @Modifying
    @Query("DELETE FROM Card c WHERE c.columnId = :columnId")
    void deleteByColumnId(@Param("columnId") Long columnId);

    @Transactional
    @Modifying
    @Query("DELETE FROM Card c WHERE c.columnId IN " +
           "(SELECT col.id FROM BoardColumn col WHERE col.boardId = :boardId)")
    void deleteByBoardId(@Param("boardId") Long boardId);
}