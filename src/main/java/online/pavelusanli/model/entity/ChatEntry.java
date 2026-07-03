package online.pavelusanli.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.pavelusanli.model.common.Role;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.ai.chat.messages.Message;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", insertable = false, updatable = false)
    private Long chatId;

    private String content;

    @Enumerated(EnumType.STRING)
    private Role role;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public static ChatEntry toChatEntry(Message message) {
        return ChatEntry.builder()
                .role(Role.getRole(message.getMessageType().getValue()))
                .content(message.getText())
                .build();
    }

    public Message toMessage() {
        return role.getMessage(content);
    }
}