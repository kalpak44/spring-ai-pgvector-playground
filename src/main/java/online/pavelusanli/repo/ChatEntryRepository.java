package online.pavelusanli.repo;

import online.pavelusanli.model.entity.ChatEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatEntryRepository extends JpaRepository<ChatEntry, Long> {

    @Query("SELECT e FROM ChatEntry e WHERE e.chatId = :chatId ORDER BY e.createdAt DESC")
    List<ChatEntry> findRecent(@Param("chatId") long chatId, Pageable pageable);

    @Transactional
    @Modifying
    @Query(value = "INSERT INTO chat_entry (chat_id, content, role, created_at) VALUES (:chatId, :content, :role, NOW())",
           nativeQuery = true)
    void insert(@Param("chatId") long chatId, @Param("content") String content, @Param("role") String role);

    @Transactional
    @Modifying
    @Query("DELETE FROM ChatEntry e WHERE e.chatId = :chatId")
    void deleteByChatId(@Param("chatId") long chatId);
}