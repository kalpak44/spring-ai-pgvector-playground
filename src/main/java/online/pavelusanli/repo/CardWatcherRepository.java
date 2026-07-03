package online.pavelusanli.repo;

import online.pavelusanli.model.entity.CardWatcher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface CardWatcherRepository extends JpaRepository<CardWatcher, Long> {

    List<CardWatcher> findByCardId(Long cardId);

    List<CardWatcher> findByCardIdIn(Collection<Long> cardIds);

    List<CardWatcher> findByUserId(Long userId);

    boolean existsByCardIdAndUserId(Long cardId, Long userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM CardWatcher w WHERE w.cardId = :cardId AND w.userId = :userId")
    void deleteByCardIdAndUserId(@Param("cardId") Long cardId, @Param("userId") Long userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM CardWatcher w WHERE w.cardId = :cardId")
    void deleteByCardId(@Param("cardId") Long cardId);

    @Transactional
    @Modifying
    @Query("DELETE FROM CardWatcher w WHERE w.cardId IN " +
           "(SELECT c.id FROM Card c WHERE c.columnId IN " +
           "(SELECT col.id FROM BoardColumn col WHERE col.boardId = :boardId))")
    void deleteByBoardId(@Param("boardId") Long boardId);
}