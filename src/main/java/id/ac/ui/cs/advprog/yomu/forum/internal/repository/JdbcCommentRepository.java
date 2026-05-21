package id.ac.ui.cs.advprog.yomu.forum.internal.repository;

import id.ac.ui.cs.advprog.yomu.forum.internal.model.Comment;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCommentRepository implements CommentRepository {

    private static final RowMapper<Comment> COMMENT_ROW_MAPPER = (rs, rowNum) -> {
        String parentComment = rs.getString("parent_comment");
        Comment comment = new Comment(
            rs.getString("user_id"),
            rs.getString("bacaan_id"),
            parentComment == null || parentComment.isBlank() ? "root" : parentComment,
            rs.getString("content")
        );
        comment.setId(rs.getString("id"));
        comment.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        comment.setUpvotes(rs.getInt("upvotes"));
        comment.setDownvotes(rs.getInt("downvotes"));
        comment.setReactionThumbsUp(rs.getInt("reaction_thumbs_up"));
        comment.setReactionHeart(rs.getInt("reaction_heart"));
        comment.setReactionLaugh(rs.getInt("reaction_laugh"));
        comment.setReactionSurprise(rs.getInt("reaction_surprise"));
        comment.setReactionSad(rs.getInt("reaction_sad"));
        return comment;
    };

    private final JdbcTemplate jdbcTemplate;

    public JdbcCommentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void createTableIfNeeded() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS comments (
                id VARCHAR(36) PRIMARY KEY,
                user_id VARCHAR(255) NOT NULL,
                bacaan_id VARCHAR(255) NOT NULL,
                parent_comment VARCHAR(255) NOT NULL DEFAULT 'root',
                content TEXT NOT NULL,
                created_at TIMESTAMP NOT NULL,
                upvotes INTEGER NOT NULL DEFAULT 0,
                downvotes INTEGER NOT NULL DEFAULT 0,
                reaction_thumbs_up INTEGER NOT NULL DEFAULT 0,
                reaction_heart INTEGER NOT NULL DEFAULT 0,
                reaction_laugh INTEGER NOT NULL DEFAULT 0,
                reaction_surprise INTEGER NOT NULL DEFAULT 0,
                reaction_sad INTEGER NOT NULL DEFAULT 0
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS comment_reactions (
                comment_id VARCHAR(36) NOT NULL,
                user_id VARCHAR(255) NOT NULL,
                reaction_type VARCHAR(50) NOT NULL,
                PRIMARY KEY (comment_id, user_id)
            )
            """);
    }

    @Override
    public Comment save(Comment comment) {
        if (comment.getId() == null || comment.getId().isBlank()) {
            comment.setId(UUID.randomUUID().toString());
        }
        if (comment.getCreatedAt() == null) {
            comment.setCreatedAt(LocalDateTime.now());
        }

        jdbcTemplate.update("""
            INSERT INTO comments (id, user_id, bacaan_id, parent_comment, content, created_at, 
                upvotes, downvotes, reaction_thumbs_up, reaction_heart, reaction_laugh, reaction_surprise, reaction_sad) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            comment.getId(),
            comment.getUserId(),
            comment.getBacaanId(),
            comment.getParentComment(),
            comment.getContent(),
            Timestamp.valueOf(comment.getCreatedAt()),
            comment.getUpvotes(),
            comment.getDownvotes(),
            comment.getReactionThumbsUp(),
            comment.getReactionHeart(),
            comment.getReactionLaugh(),
            comment.getReactionSurprise(),
            comment.getReactionSad()
        );
        return comment;
    }

    @Override
    public Optional<Comment> findById(String id) {
        return jdbcTemplate.query(
            "SELECT * FROM comments WHERE id = ?",
            COMMENT_ROW_MAPPER,
            id
        ).stream().findFirst();
    }

    @Override
    public int updateContentById(String id, String content) {
        return jdbcTemplate.update(
            "UPDATE comments SET content = ? WHERE id = ?",
            content,
            id
        );
    }

    @Override
    public int deleteById(String id) {
        jdbcTemplate.update("DELETE FROM comment_reactions WHERE comment_id = ?", id);
        return jdbcTemplate.update("DELETE FROM comments WHERE id = ?", id);
    }

    @Override
    public void addReaction(String commentId, String userId, String reactionType) {
        // 1. Check if user already reacted
        String existingReaction = jdbcTemplate.queryForList(
            "SELECT reaction_type FROM comment_reactions WHERE comment_id = ? AND user_id = ?",
            String.class, commentId, userId
        ).stream().findFirst().orElse(null);

        if (existingReaction != null) {
            if (existingReaction.equalsIgnoreCase(reactionType)) {
                return; // No change
            }
            // Decrement old reaction counter
            decrementCounter(commentId, existingReaction);
            // Update reaction record
            jdbcTemplate.update(
                "UPDATE comment_reactions SET reaction_type = ? WHERE comment_id = ? AND user_id = ?",
                reactionType, commentId, userId
            );
        } else {
            // New reaction record
            jdbcTemplate.update(
                "INSERT INTO comment_reactions (comment_id, user_id, reaction_type) VALUES (?, ?, ?)",
                commentId, userId, reactionType
            );
        }

        // Increment new reaction counter
        incrementCounter(commentId, reactionType);
    }

    private void incrementCounter(String commentId, String type) {
        String column = mapReactionToColumn(type);
        jdbcTemplate.update("UPDATE comments SET " + column + " = " + column + " + 1 WHERE id = ?", commentId);
    }

    private void decrementCounter(String commentId, String type) {
        String column = mapReactionToColumn(type);
        jdbcTemplate.update("UPDATE comments SET " + column + " = GREATEST(0, " + column + " - 1) WHERE id = ?", commentId);
    }

    private String mapReactionToColumn(String type) {
        return switch (type.toLowerCase()) {
            case "upvote" -> "upvotes";
            case "downvote" -> "downvotes";
            case "thumbs_up" -> "reaction_thumbs_up";
            case "heart" -> "reaction_heart";
            case "laugh" -> "reaction_laugh";
            case "surprise" -> "reaction_surprise";
            case "sad" -> "reaction_sad";
            default -> throw new IllegalArgumentException("Unknown reaction type: " + type);
        };
    }

    @Override
    public List<Comment> findAll() {
        return jdbcTemplate.query(
            """
            SELECT * FROM comments
            ORDER BY (upvotes + reaction_thumbs_up + reaction_heart + reaction_laugh + reaction_surprise + reaction_sad - downvotes) DESC,
                     created_at DESC
            """,
            COMMENT_ROW_MAPPER
        );
    }

    @Override
    public List<Comment> findByBacaanId(String bacaanId) {
        return jdbcTemplate.query(
            """
            SELECT * FROM comments
            WHERE bacaan_id = ?
            ORDER BY (upvotes + reaction_thumbs_up + reaction_heart + reaction_laugh + reaction_surprise + reaction_sad - downvotes) DESC,
                     created_at DESC
            """,
            COMMENT_ROW_MAPPER,
            bacaanId
        );
    }
}



