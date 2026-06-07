package id.ac.ui.cs.advprog.yomu.forum.internal.service;

import java.time.Instant;

public record CommentResponse(
		String id,
		String userId,
		String username,
		String displayName,
		String bacaanId,
		String parentComment,
		String content,
		Instant createdAt,
		int upvotes,
		int downvotes,
		int thumbsUp,
		int heart,
		int laugh,
		int surprise,
		int sad) {
}
