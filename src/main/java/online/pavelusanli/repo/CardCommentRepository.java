package online.pavelusanli.repo;

import online.pavelusanli.model.entity.CardComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CardCommentRepository extends JpaRepository<CardComment, Long> {

    @Query(value = "SELECT c FROM CardComment c WHERE c.cardId = :cardId ORDER BY c.createdAt ASC",
           countQuery = "SELECT COUNT(c) FROM CardComment c WHERE c.cardId = :cardId")
    Page<CardComment> findByCardId(@Param("cardId") Long cardId, Pageable pageable);

    long countByCardId(Long cardId);

    @Transactional
    @Modifying
    @Query("DELETE FROM CardComment c WHERE c.cardId = :cardId")
    void deleteByCardId(@Param("cardId") Long cardId);

    @Transactional
    @Modifying
    @Query("DELETE FROM CardComment c WHERE c.cardId IN " +
           "(SELECT card.id FROM Card card WHERE card.columnId IN " +
           "(SELECT col.id FROM BoardColumn col WHERE col.boardId = :boardId))")
    void deleteByBoardId(@Param("boardId") Long boardId);
}