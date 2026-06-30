package online.pavelusanli.repo;

import online.pavelusanli.model.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
}