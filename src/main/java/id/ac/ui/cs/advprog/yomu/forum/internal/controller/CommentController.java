package id.ac.ui.cs.advprog.yomu.forum.internal.controller;

import id.ac.ui.cs.advprog.yomu.shared.event.CommentCreatedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentDeletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentUpdatedEvent;
import id.ac.ui.cs.advprog.yomu.forum.internal.service.CommentResponse;
import id.ac.ui.cs.advprog.yomu.forum.internal.service.CommentService;
import id.ac.ui.cs.advprog.yomu.forum.internal.service.CommentTreeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/forum/comments")
public class CommentController {

	private final CommentService commentService;

	public CommentController(CommentService commentService) {
		this.commentService = commentService;
	}

	@PostMapping
	public ResponseEntity<CommentCreatedEvent> createComment(@Valid @RequestBody CreateCommentRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(commentService.createComment(
				request.userId(),
				request.bacaanId(),
				request.commentContent(),
				request.parentComment()
			));
	}

	@GetMapping
	public List<CommentResponse> getComments(@RequestParam(required = false) String bacaanId) {
		return commentService.listComments(bacaanId);
	}

	@GetMapping("/tree")
	public List<CommentTreeResponse> getCommentsTree(@RequestParam(required = false) String bacaanId) {
		return commentService.listCommentsTree(bacaanId);
	}

	@PutMapping("/{commentId}")
	public CommentUpdatedEvent updateComment(
		@PathVariable String commentId,
		@Valid @RequestBody UpdateCommentRequest request
	) {
		return commentService.updateComment(commentId, request.commentContent());
	}

	@DeleteMapping("/{commentId}")
	public CommentDeletedEvent deleteComment(@PathVariable String commentId) {
		return commentService.deleteComment(commentId);
	}
}
