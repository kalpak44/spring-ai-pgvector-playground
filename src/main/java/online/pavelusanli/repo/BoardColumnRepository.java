package online.pavelusanli.repo;

import online.pavelusanli.model.entity.BoardColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {
    List<BoardColumn> findAllByOrderByPositionAsc();
    int countByPositionGreaterThan(int position);
}