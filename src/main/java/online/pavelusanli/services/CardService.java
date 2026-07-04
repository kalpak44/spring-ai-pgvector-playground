package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.model.common.CardPriority;
import online.pavelusanli.model.entity.*;
import online.pavelusanli.repo.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {

    private final BoardService boardService;
    private final BoardColumnRepository boardColumnRepo;
    private final CardRepository cardRepo;
    private final CardAssignmentRepository cardAssignmentRepo;
    private final CardWatcherRepository cardWatcherRepo;
    private final CardCommentRepository cardCommentRepo;

    @Transactional
    public Card createCard(Long boardId, Long columnId, Long createdBy,
                           String title, String description,
                           CardPriority priority, String color, LocalDateTime deadline,
                           List<Long> assigneeIds, List<Long> watcherIds) {
        boardService.getBoardById(boardId, createdBy);
        requireColumnOnBoard(columnId, boardId);

        int nextPosition = cardRepo.findMaxPositionByColumnId(columnId) + 1;
        Card card = cardRepo.save(Card.builder()
                .columnId(columnId)
                .title(title)
                .description(blankToNull(description))
                .position(nextPosition)
                .priority(priority)
                .color(blankToNull(color))
                .deadline(deadline)
                .createdBy(createdBy)
                .build());

        saveAssignees(card.getId(), assigneeIds);
        saveWatchers(card.getId(), watcherIds);

        log.debug("Card {} created in column {} of board {} by {}", card.getId(), columnId, boardId, createdBy);
        return card;
    }

    public Card getCard(Long boardId, Long cardId, Long userId) {
        boardService.getBoardById(boardId, userId);
        Card card = cardRepo.findById(cardId)
                .orElseThrow(() -> new NoSuchElementException("Card not found: " + cardId));
        requireCardOnBoard(card, boardId);
        return card;
    }

    @Transactional
    public Card updateCard(Long boardId, Long cardId, Long userId,
                           String title, String description,
                           CardPriority priority, String color, LocalDateTime deadline,
                           List<Long> assigneeIds, List<Long> watcherIds) {
        Card card = getCard(boardId, cardId, userId);
        card.setTitle(title);
        card.setDescription(blankToNull(description));
        card.setPriority(priority);
        card.setColor(blankToNull(color));
        card.setDeadline(deadline);
        card = cardRepo.save(card);

        cardAssignmentRepo.deleteByCardId(cardId);
        saveAssignees(cardId, assigneeIds);

        cardWatcherRepo.deleteByCardId(cardId);
        saveWatchers(cardId, watcherIds);

        log.debug("Card {} updated in board {} by {}", cardId, boardId, userId);
        return card;
    }

    @Transactional
    public void deleteCard(Long boardId, Long cardId, Long userId) {
        Card card = getCard(boardId, cardId, userId);
        cardCommentRepo.deleteByCardId(cardId);
        cardWatcherRepo.deleteByCardId(cardId);
        cardAssignmentRepo.deleteByCardId(cardId);
        cardRepo.delete(card);
        log.debug("Card {} deleted from board {} by {}", cardId, boardId, userId);
    }

    @Transactional
    public Card moveCard(Long boardId, Long cardId, Long newColumnId, Long userId) {
        boardService.getBoardById(boardId, userId);
        requireColumnOnBoard(newColumnId, boardId);
        Card card = cardRepo.findById(cardId)
                .orElseThrow(() -> new NoSuchElementException("Card not found: " + cardId));
        requireCardOnBoard(card, boardId);

        int nextPosition = cardRepo.findMaxPositionByColumnId(newColumnId) + 1;
        card.setColumnId(newColumnId);
        card.setPosition(nextPosition);
        return cardRepo.save(card);
    }

    @Transactional
    public CardComment addComment(Long boardId, Long cardId, Long authorId, String content) {
        Card card = getCard(boardId, cardId, authorId);
        CardComment comment = cardCommentRepo.save(CardComment.builder()
                .cardId(card.getId()).authorId(authorId).content(content).build());
        log.debug("Comment {} added to card {} by {}", comment.getId(), cardId, authorId);
        return comment;
    }

    @Transactional
    public CardComment updateComment(Long boardId, Long cardId, Long commentId, Long userId, String content) {
        boardService.getBoardById(boardId, userId);
        CardComment comment = cardCommentRepo.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found: " + commentId));
        if (!comment.getCardId().equals(cardId)) {
            throw new NoSuchElementException("Comment does not belong to card");
        }
        if (!comment.getAuthorId().equals(userId)) {
            throw new AccessDeniedException("Cannot edit comment " + commentId);
        }
        comment.setContent(content);
        return cardCommentRepo.save(comment);
    }

    @Transactional
    public void deleteComment(Long boardId, Long cardId, Long commentId, Long userId) {
        boardService.getBoardById(boardId, userId);
        CardComment comment = cardCommentRepo.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found: " + commentId));
        if (!comment.getCardId().equals(cardId)) {
            throw new NoSuchElementException("Comment does not belong to card");
        }
        if (!comment.getAuthorId().equals(userId)) {
            throw new AccessDeniedException("Cannot delete comment " + commentId);
        }
        cardCommentRepo.delete(comment);
        log.debug("Comment {} deleted from card {} by {}", commentId, cardId, userId);
    }

    private void saveAssignees(Long cardId, List<Long> assigneeIds) {
        if (assigneeIds == null) return;
        assigneeIds.stream().distinct().forEach(uid ->
                cardAssignmentRepo.save(CardAssignment.builder().cardId(cardId).userId(uid).build()));
    }

    private void saveWatchers(Long cardId, List<Long> watcherIds) {
        if (watcherIds == null) return;
        watcherIds.stream().distinct().forEach(uid ->
                cardWatcherRepo.save(CardWatcher.builder().cardId(cardId).userId(uid).build()));
    }

    private void requireColumnOnBoard(Long columnId, Long boardId) {
        boardColumnRepo.findById(columnId)
                .filter(c -> c.getBoardId().equals(boardId))
                .orElseThrow(() -> new NoSuchElementException("Column " + columnId + " not on board " + boardId));
    }

    private void requireCardOnBoard(Card card, Long boardId) {
        boardColumnRepo.findById(card.getColumnId())
                .filter(c -> c.getBoardId().equals(boardId))
                .orElseThrow(() -> new AccessDeniedException(
                        "Card " + card.getId() + " does not belong to board " + boardId));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}