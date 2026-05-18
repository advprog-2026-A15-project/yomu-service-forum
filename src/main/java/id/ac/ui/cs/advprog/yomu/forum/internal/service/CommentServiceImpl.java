package id.ac.ui.cs.advprog.yomu.forum.internal.service;

import id.ac.ui.cs.advprog.yomu.shared.event.CommentCreatedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentDeletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentUpdatedEvent;
import id.ac.ui.cs.advprog.yomu.forum.internal.model.Comment;
import id.ac.ui.cs.advprog.yomu.forum.internal.repository.CommentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommentServiceImpl implements CommentService {
	private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

	private final CommentRepository commentRepository;
	private final RabbitTemplate rabbitTemplate;
	private final Clock clock;

	public CommentServiceImpl(
			CommentRepository commentRepository,
			RabbitTemplate rabbitTemplate,
			Clock clock) {
		this.commentRepository = commentRepository;
		this.rabbitTemplate = rabbitTemplate;
		this.clock = clock;
	}

	@Override
	@Transactional
	public CommentCreatedEvent createComment(String userId, String bacaanId, String commentContent) {
		return createComment(userId, bacaanId, commentContent, "root");
	}

	@Override
	@Transactional
	public CommentCreatedEvent createComment(String userId, String bacaanId, String commentContent,
			String parentComment) {
		String normalizedParent = (parentComment == null || parentComment.isBlank()) ? "root" : parentComment;
		validateParentComment(bacaanId, normalizedParent);

		Instant timestamp = clock.instant();
		String sanitizedContent = sanitize(commentContent);
		Comment comment = new Comment(userId, bacaanId, normalizedParent, sanitizedContent);
		comment.setCreatedAt(LocalDateTime.ofInstant(timestamp, clock.getZone()));

		Comment savedComment = commentRepository.save(comment);
		CommentCreatedEvent event = new CommentCreatedEvent(
				savedComment.getUserId(),
				savedComment.getBacaanId(),
				savedComment.getParentComment(),
				savedComment.getId(),
				sanitizedContent,
				timestamp);
		rabbitTemplate.convertAndSend("yomu.comment.created", event);
		return event;
	}

	@Override
	@Transactional
	public CommentUpdatedEvent updateComment(String commentId, String commentContent) {
		return updateComment(commentId, commentContent, null, null);
	}

	@Override
	@Transactional
	public CommentUpdatedEvent updateComment(String commentId, String commentContent, String userId, String role) {
		Comment existingComment = getCommentOrThrow(commentId);
		validateModerationPermission(existingComment, userId, role, "mengedit");

		Instant timestamp = clock.instant();
		String sanitizedContent = sanitize(commentContent);
		commentRepository.updateContentById(commentId, sanitizedContent);
		log.info(
			"Moderation update on comment {} by user {} as {}",
			commentId,
			userId,
			isAdmin(role) ? "admin" : "author"
		);

		CommentUpdatedEvent event = new CommentUpdatedEvent(
				existingComment.getUserId(),
				existingComment.getBacaanId(),
				existingComment.getParentComment(),
				commentId,
				sanitizedContent,
				timestamp);
		rabbitTemplate.convertAndSend("yomu.comment.updated", event);
		return event;
	}

	@Override
	@Transactional
	public CommentDeletedEvent deleteComment(String commentId) {
		return deleteComment(commentId, null, null);
	}

	@Override
	@Transactional
	public CommentDeletedEvent deleteComment(String commentId, String userId, String role) {
		Comment existingComment = getCommentOrThrow(commentId);
		validateModerationPermission(existingComment, userId, role, "menghapus");

		Instant timestamp = clock.instant();
		commentRepository.deleteById(commentId);
		log.info(
			"Moderation delete on comment {} by user {} as {}",
			commentId,
			userId,
			isAdmin(role) ? "admin" : "author"
		);

		CommentDeletedEvent event = new CommentDeletedEvent(
				existingComment.getUserId(),
				existingComment.getBacaanId(),
				existingComment.getParentComment(),
				existingComment.getId(),
				existingComment.getContent(),
				timestamp);
		rabbitTemplate.convertAndSend("yomu.comment.deleted", event);
		return event;
	}

	@Override
	@Transactional
	public void addReaction(String commentId, String userId, String reactionType) {
		getCommentOrThrow(commentId);
		commentRepository.addReaction(commentId, userId, reactionType);
	}

	@Override
	public List<CommentResponse> listComments(String bacaanId) {
		List<Comment> comments = (bacaanId == null || bacaanId.isBlank())
				? commentRepository.findAll()
				: commentRepository.findByBacaanId(bacaanId);

		return comments.stream()
				.map(this::toCommentResponse)
				.toList();
	}

	@Override
	public List<CommentTreeResponse> listCommentsTree(String bacaanId) {
		List<Comment> comments = (bacaanId == null || bacaanId.isBlank())
				? commentRepository.findAll()
				: commentRepository.findByBacaanId(bacaanId);

		Map<String, MutableTreeNode> nodesById = new LinkedHashMap<>();
		for (Comment comment : comments) {
			nodesById.put(comment.getId(), new MutableTreeNode(comment));
		}

		List<MutableTreeNode> roots = new ArrayList<>();
		for (MutableTreeNode node : nodesById.values()) {
			if ("root".equals(node.comment.getParentComment())) {
				roots.add(node);
				continue;
			}

			MutableTreeNode parent = nodesById.get(node.comment.getParentComment());
			if (parent == null) {
				roots.add(node);
				continue;
			}

			parent.children.add(node);
		}

		return roots.stream().map(this::toTreeResponse).toList();
	}

	@Override
	public CommentResponse getComment(String commentId) {
		Comment comment = getCommentOrThrow(commentId);
		return toCommentResponse(comment);
	}

	private CommentResponse toCommentResponse(Comment comment) {
		return new CommentResponse(
				comment.getId(),
				comment.getUserId(),
				comment.getBacaanId(),
				comment.getParentComment(),
				comment.getContent(),
				comment.getCreatedAt().atZone(clock.getZone()).toInstant(),
				comment.getUpvotes(),
				comment.getDownvotes(),
				comment.getReactionThumbsUp(),
				comment.getReactionHeart(),
				comment.getReactionLaugh(),
				comment.getReactionSurprise(),
				comment.getReactionSad());
	}

	private CommentTreeResponse toTreeResponse(MutableTreeNode node) {
		return new CommentTreeResponse(
				node.comment.getId(),
				node.comment.getUserId(),
				node.comment.getBacaanId(),
				node.comment.getParentComment(),
				node.comment.getContent(),
				node.comment.getCreatedAt().atZone(clock.getZone()).toInstant(),
				node.comment.getUpvotes(),
				node.comment.getDownvotes(),
				node.comment.getReactionThumbsUp(),
				node.comment.getReactionHeart(),
				node.comment.getReactionLaugh(),
				node.comment.getReactionSurprise(),
				node.comment.getReactionSad(),
				node.children.stream().map(this::toTreeResponse).toList());
	}

	private static final class MutableTreeNode {
		private final Comment comment;
		private final List<MutableTreeNode> children = new ArrayList<>();

		private MutableTreeNode(Comment comment) {
			this.comment = comment;
		}
	}

	private void validateParentComment(String bacaanId, String parentComment) {
		if ("root".equals(parentComment)) {
			return;
		}

		Comment parent = commentRepository.findById(parentComment)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent comment not found"));

		if (!bacaanId.equals(parent.getBacaanId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent comment must belong to the same bacaan");
		}
	}

	private Comment getCommentOrThrow(String commentId) {
		return commentRepository.findById(commentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
	}

	private void validateModerationPermission(Comment existingComment, String userId, String role, String action) {
		if (userId == null || userId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User tidak terautentikasi");
		}

		if (isAdmin(role) || existingComment.getUserId().equals(userId)) {
			return;
		}

		log.warn(
			"Rejected moderation {} on comment {} by user {}",
			action,
			existingComment.getId(),
			userId
		);
		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya admin atau penulis komentar yang bisa " + action + " komentar ini");
	}

	private boolean isAdmin(String role) {
		return "ADMIN".equalsIgnoreCase(role);
	}

	private String sanitize(String content) {
		if (content == null) {
			return "";
		}
		return content
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}
}
