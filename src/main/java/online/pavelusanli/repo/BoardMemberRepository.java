package online.pavelusanli.repo;

import online.pavelusanli.model.common.BoardMemberRole;
import online.pavelusanli.model.entity.BoardMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface BoardMemberRepository extends JpaRepository<BoardMember, Long> {

    List<BoardMember> findByBoardId(Long boardId);

    List<BoardMember> findByUserId(Long userId);

    Optional<BoardMember> findByBoardIdAndUserId(Long boardId, Long userId);

    boolean existsByBoardIdAndUserId(Long boardId, Long userId);

    boolean existsByBoardIdAndUserIdAndRole(Long boardId, Long userId, BoardMemberRole role);

    @Transactional
    @Modifying
    @Query("DELETE FROM BoardMember m WHERE m.boardId = :boardId AND m.userId = :userId")
    void deleteByBoardIdAndUserId(@Param("boardId") Long boardId, @Param("userId") Long userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM BoardMember m WHERE m.boardId = :boardId")
    void deleteByBoardId(@Param("boardId") Long boardId);
}