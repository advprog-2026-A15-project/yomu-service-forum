package id.ac.ui.cs.advprog.yomu.forum.internal.controller;

import jakarta.validation.constraints.NotBlank;

public record CreateCommentRequest(
    @NotBlank String userId,
    @NotBlank String bacaanId,
    @NotBlank String commentContent,
    String parentComment
) {
}

