package id.ac.ui.cs.advprog.yomu.forum.internal.service;

import id.ac.ui.cs.advprog.yomu.shared.event.CommentCreatedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentDeletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentUpdatedEvent;

import java.util.List;

public interface CommentService {

	default CommentCreatedEvent createComment(String userId, String bacaanId, String commentContent) {
		return createComment(userId, bacaanId, commentContent, "root");
	}

	CommentCreatedEvent createComment(String userId, String bacaanId, String commentContent, String parentComment);

	CommentUpdatedEvent updateComment(String commentId, String commentContent);

	CommentUpdatedEvent updateComment(String commentId, String commentContent, String userId, String role);

	CommentDeletedEvent deleteComment(String commentId);

	CommentDeletedEvent deleteComment(String commentId, String userId, String role);

	void addReaction(String commentId, String userId, String reactionType);

	List<CommentResponse> listComments(String bacaanId);

	List<CommentTreeResponse> listCommentsTree(String bacaanId);

	CommentResponse getComment(String commentId);
}
