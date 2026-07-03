package online.pavelusanli.repo;

import online.pavelusanli.model.entity.Board;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BoardRepository extends JpaRepository<Board, Long> {

    List<Board> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    // All boards where the user is a member (including ones they own)
    @Query("SELECT b FROM Board b WHERE b.id IN " +
           "(SELECT m.boardId FROM BoardMember m WHERE m.userId = :userId) " +
           "ORDER BY b.updatedAt DESC")
    List<Board> findAccessibleByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT b FROM Board b WHERE b.id IN " +
                   "(SELECT m.boardId FROM BoardMember m WHERE m.userId = :userId) AND " +
                   "(:q = '' OR LOWER(b.name) LIKE LOWER(CONCAT('%', :q, '%')))",
           countQuery = "SELECT COUNT(b) FROM Board b WHERE b.id IN " +
                        "(SELECT m.boardId FROM BoardMember m WHERE m.userId = :userId) AND " +
                        "(:q = '' OR LOWER(b.name) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Board> searchAccessibleByUserId(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);

    boolean existsByIdAndOwnerId(Long id, Long ownerId);
}