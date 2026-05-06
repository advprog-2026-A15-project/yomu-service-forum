package id.ac.ui.cs.advprog.yomu.forum.internal.controller;

import jakarta.validation.constraints.NotBlank;

public record UpdateCommentRequest(
    @NotBlank String commentContent
) {
}

