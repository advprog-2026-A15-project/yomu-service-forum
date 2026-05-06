package id.ac.ui.cs.advprog.yomu.forum.internal.service;

import java.time.Instant;

public record CommentResponse(
    String commentId,
    String userId,
    String bacaanId,
    String parentComment,
    String commentContent,
    Instant timestamp
) {
}

