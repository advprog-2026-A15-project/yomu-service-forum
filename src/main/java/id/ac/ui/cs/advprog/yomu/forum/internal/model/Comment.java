package id.ac.ui.cs.advprog.yomu.forum.internal.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class Comment {

    private String id;

    private String userId;

    private String bacaanId;

    private String parentComment = "root";

    private String content;

    private LocalDateTime createdAt;

    private int upvotes = 0;
    private int downvotes = 0;
    private int reactionThumbsUp = 0;
    private int reactionHeart = 0;
    private int reactionLaugh = 0;
    private int reactionSurprise = 0;
    private int reactionSad = 0;

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