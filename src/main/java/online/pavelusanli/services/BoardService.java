package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.model.common.BoardMemberRole;
import online.pavelusanli.model.entity.Board;
import online.pavelusanli.model.entity.BoardMember;
import online.pavelusanli.repo.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepo;
    private final BoardMemberRepository boardMemberRepo;
    private final BoardColumnRepository boardColumnRepo;
    private final CardRepository cardRepo;
    private final CardAssignmentRepository cardAssignmentRepo;
    private final CardWatcherRepository cardWatcherRepo;
    private final CardCommentRepository cardCommentRepo;
    private final BoardActivityRepository boardActivityRepo;

    @Transactional
    public Board createBoard(String name, String description, Long ownerId) {
        Board board = boardRepo.save(Board.builder()
                .name(name)
                .description(description)
                .ownerId(ownerId)
                .build());

        boardMemberRepo.save(BoardMember.builder()
                .boardId(board.getId())
                .userId(ownerId)
                .role(BoardMemberRole.OWNER)
                .build());

        log.debug("Board {} created by user {}", board.getId(), ownerId);
        return board;
    }

    public List<Board> getBoardsForUser(Long userId) {
        return boardRepo.findAccessibleByUserId(userId);
    }

    public Board getBoardById(Long boardId, Long userId) {
        Board board = boardRepo.findById(boardId)
                .orElseThrow(() -> new NoSuchElementException("Board not found: " + boardId));
        requireAccess(boardId, userId);
        return board;
    }

    @Transactional
    public Board updateBoardName(Long boardId, Long userId, String name) {
        Board board = requireOwned(boardId, userId);
        board.setName(name);
        return boardRepo.save(board);
    }

    @Transactional
    public Board updateBoardDescription(Long boardId, Long userId, String description) {
        Board board = requireOwned(boardId, userId);
        board.setDescription(description);
        return boardRepo.save(board);
    }

    @Transactional
    public void addMember(Long boardId, Long actorId, Long targetUserId) {
        requireOwned(boardId, actorId);
        if (boardMemberRepo.existsByBoardIdAndUserId(boardId, targetUserId)) {
            return;
        }
        boardMemberRepo.save(BoardMember.builder()
                .boardId(boardId)
                .userId(targetUserId)
                .role(BoardMemberRole.MEMBER)
                .build());
        log.debug("User {} added to board {} by {}", targetUserId, boardId, actorId);
    }

    @Transactional
    public void removeMember(Long boardId, Long actorId, Long targetUserId) {
        Board board = requireOwned(boardId, actorId);
        if (board.getOwnerId().equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot remove the board owner");
        }
        boardMemberRepo.deleteByBoardIdAndUserId(boardId, targetUserId);
        log.debug("User {} removed from board {} by {}", targetUserId, boardId, actorId);
    }

    @Transactional
    public void deleteBoard(Long boardId, Long userId) {
        requireOwned(boardId, userId);

        // No DB-level CASCADE — delete child rows in FK dependency order.
        cardCommentRepo.deleteByBoardId(boardId);
        cardWatcherRepo.deleteByBoardId(boardId);
        cardAssignmentRepo.deleteByBoardId(boardId);
        cardRepo.deleteByBoardId(boardId);
        boardColumnRepo.deleteByBoardId(boardId);
        boardMemberRepo.deleteByBoardId(boardId);
        boardActivityRepo.deleteByBoardId(boardId);
        boardRepo.deleteById(boardId);

        log.debug("Board {} deleted by user {}", boardId, userId);
    }

    public boolean hasAccess(Long boardId, Long userId) {
        return boardMemberRepo.existsByBoardIdAndUserId(boardId, userId);
    }

    public boolean isOwner(Long boardId, Long userId) {
        return boardRepo.existsByIdAndOwnerId(boardId, userId);
    }

    private void requireAccess(Long boardId, Long userId) {
        if (!hasAccess(boardId, userId)) {
            throw new AccessDeniedException("User " + userId + " has no access to board " + boardId);
        }
    }

    private Board requireOwned(Long boardId, Long userId) {
        Board board = boardRepo.findById(boardId)
                .orElseThrow(() -> new NoSuchElementException("Board not found: " + boardId));
        if (!board.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("User " + userId + " is not the owner of board " + boardId);
        }
        return board;
    }
}