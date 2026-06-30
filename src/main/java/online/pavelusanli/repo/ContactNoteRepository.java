package online.pavelusanli.repo;

import online.pavelusanli.model.ContactNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactNoteRepository extends JpaRepository<ContactNote, Long> {
}