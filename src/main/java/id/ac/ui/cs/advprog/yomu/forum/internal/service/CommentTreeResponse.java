package id.ac.ui.cs.advprog.yomu.forum.internal.service;

import java.time.Instant;
import java.util.List;

public record CommentTreeResponse(
    String commentId,
    String userId,
    String bacaanId,
    String parentComment,
    String commentContent,
    Instant timestamp,
    List<CommentTreeResponse> children
) {
}

