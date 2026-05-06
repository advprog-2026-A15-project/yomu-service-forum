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
                created_at TIMESTAMP NOT NULL
            )
            """);
        jdbcTemplate.execute("ALTER TABLE comments ADD COLUMN IF NOT EXISTS parent_comment VARCHAR(255) NOT NULL DEFAULT 'root'");
    }

    @Override
    public Comment save(Comment comment) {
        if (comment.getId() == null || comment.getId().isBlank()) {
            comment.setId(UUID.randomUUID().toString());
        }
        if (comment.getCreatedAt() == null) {
            comment.setCreatedAt(LocalDateTime.now());
        }

        jdbcTemplate.update(
            "INSERT INTO comments (id, user_id, bacaan_id, parent_comment, content, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            comment.getId(),
            comment.getUserId(),
            comment.getBacaanId(),
            comment.getParentComment(),
            comment.getContent(),
            Timestamp.valueOf(comment.getCreatedAt())
        );
        return comment;
    }

    @Override
    public Optional<Comment> findById(String id) {
        return jdbcTemplate.query(
            "SELECT id, user_id, bacaan_id, parent_comment, content, created_at FROM comments WHERE id = ?",
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
        return jdbcTemplate.update("DELETE FROM comments WHERE id = ?", id);
    }

    @Override
    public List<Comment> findAll() {
        return jdbcTemplate.query(
            "SELECT id, user_id, bacaan_id, parent_comment, content, created_at FROM comments ORDER BY created_at DESC",
            COMMENT_ROW_MAPPER
        );
    }

    @Override
    public List<Comment> findByBacaanId(String bacaanId) {
        return jdbcTemplate.query(
            "SELECT id, user_id, bacaan_id, parent_comment, content, created_at FROM comments WHERE bacaan_id = ? ORDER BY created_at DESC",
            COMMENT_ROW_MAPPER,
            bacaanId
        );
    }
}



