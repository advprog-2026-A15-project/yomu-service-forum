package id.ac.ui.cs.advprog.yomu.forum.internal.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments", schema = "FORUM")
@Getter
@Setter
@NoArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // SOFT LINK: Menunjuk ke User di modul Auth, tanpa @ManyToOne
    @Column(name = "user_id", nullable = false)
    private String userId;

    // SOFT LINK: Menunjuk ke Bacaan di modul Learning, tanpa @ManyToOne
    @Column(name = "bacaan_id", nullable = false)
    private String bacaanId;

    @Column(name = "parent_comment", nullable = false)
    private String parentComment = "root";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Otomatis mengisi timestamp saat data di-save ke database pertama kali
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.parentComment == null || this.parentComment.isBlank()) {
            this.parentComment = "root";
        }
    }

    public Comment(String userId, String bacaanId, String content) {
        this.userId = userId;
        this.bacaanId = bacaanId;
        this.content = content;
    }

    public Comment(String userId, String bacaanId, String parentComment, String content) {
        this.userId = userId;
        this.bacaanId = bacaanId;
        this.parentComment = (parentComment == null || parentComment.isBlank()) ? "root" : parentComment;
        this.content = content;
    }
}