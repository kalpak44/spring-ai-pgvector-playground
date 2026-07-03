package online.pavelusanli.repo;

import online.pavelusanli.model.entity.BoardColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

    List<BoardColumn> findByBoardIdOrderByPosition(Long boardId);

    boolean existsByBoardIdAndName(Long boardId, String name);

    @Query("SELECT COALESCE(MAX(c.position), 0) FROM BoardColumn c WHERE c.boardId = :boardId")
    int findMaxPositionByBoardId(@Param("boardId") Long boardId);

    @Transactional
    @Modifying
    @Query("DELETE FROM BoardColumn c WHERE c.boardId = :boardId")
    void deleteByBoardId(@Param("boardId") Long boardId);
}