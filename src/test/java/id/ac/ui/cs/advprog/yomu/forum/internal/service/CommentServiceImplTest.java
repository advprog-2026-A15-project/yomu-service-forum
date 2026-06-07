package id.ac.ui.cs.advprog.yomu.forum.internal.service;

import id.ac.ui.cs.advprog.yomu.forum.internal.model.Comment;
import id.ac.ui.cs.advprog.yomu.forum.internal.repository.CommentRepository;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentCreatedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentDeletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentUpdatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CommentServiceImplTest {

	private CommentRepository mockRepo;
	private RabbitTemplateStub stubRabbit;
	private Clock fixedClock;
	private SimpleMeterRegistry meterRegistry;
	private CommentServiceImpl service;

	@BeforeEach
	void setUp() {
		mockRepo = mock(CommentRepository.class);
		stubRabbit = new RabbitTemplateStub();
		fixedClock = Clock.fixed(Instant.parse("2026-04-23T10:00:00Z"), ZoneId.of("UTC"));
		meterRegistry = new SimpleMeterRegistry();
		service = new CommentServiceImpl(
				mockRepo,
				stubRabbit,
				fixedClock,
				meterRegistry,
				userId -> Optional.empty()
		);
	}

	@Test
	void createComment_withValidData_shouldSaveAndPublishEvent() {
		Comment saved = new Comment("user1", "bacaan1", "root", "Test content");
		saved.setId("comment-1");
		saved.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.save(any(Comment.class))).thenReturn(saved);

		CommentCreatedEvent result = service.createComment("user1", "bacaan1", "Test content", "root");

		assertNotNull(result);
		assertEquals("user1", result.userId());
		assertEquals("bacaan1", result.bacaanId());
		assertEquals("Test content", result.commentContent());
		assertEquals("comment-1", result.commentId());
		assertEquals(1, stubRabbit.published.size());
		assertEquals("yomu.comment.created", stubRabbit.published.getFirst().routingKey());
		assertInstanceOf(CommentCreatedEvent.class, stubRabbit.published.getFirst().payload());
		assertEquals(1.0, meterRegistry.counter("yomu_forum_comment_actions_total", "action", "create", "outcome", "success").count());
	}

	@Test
	void createComment_shouldEscapeHtmlContentBeforePersistingAndPublishing() {
		Comment saved = new Comment("user1", "bacaan1", "root", "&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;");
		saved.setId("comment-1");
		saved.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.save(any(Comment.class))).thenReturn(saved);

		CommentCreatedEvent result = service.createComment("user1", "bacaan1", "<script>alert('x')</script>", "root");

		assertEquals("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;", result.commentContent());
		verify(mockRepo).save(argThat(c -> "&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;".equals(c.getContent())));
	}

	@Test
	void createComment_withNullParent_shouldDefaultToRoot() {
		Comment saved = new Comment("user1", "bacaan1", "root", "Test");
		saved.setId("comment-1");
		saved.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.save(any(Comment.class))).thenReturn(saved);

		service.createComment("user1", "bacaan1", "Test", null);

		verify(mockRepo, times(1)).save(argThat(c -> "root".equals(c.getParentComment())));
	}

	@Test
	void createComment_withBlankParent_shouldDefaultToRoot() {
		Comment saved = new Comment("user1", "bacaan1", "root", "Test");
		saved.setId("comment-1");
		saved.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.save(any(Comment.class))).thenReturn(saved);

		service.createComment("user1", "bacaan1", "Test", "  ");

		verify(mockRepo, times(1)).save(argThat(c -> "root".equals(c.getParentComment())));
	}

	@Test
	void createComment_withValidParentId_shouldAccept() {
		Comment parent = new Comment("user0", "bacaan1", "root", "Parent");
		parent.setId("parent-1");

		Comment saved = new Comment("user1", "bacaan1", "parent-1", "Child");
		saved.setId("comment-1");
		saved.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("parent-1")).thenReturn(Optional.of(parent));
		when(mockRepo.save(any(Comment.class))).thenReturn(saved);

		CommentCreatedEvent result = service.createComment("user1", "bacaan1", "Child", "parent-1");

		assertNotNull(result);
		assertEquals("parent-1", result.parentComment());
	}

	@Test
	void createComment_withNonExistentParent_shouldThrow() {
		when(mockRepo.findById("nonexistent")).thenReturn(Optional.empty());

		assertThrows(ResponseStatusException.class,
			() -> service.createComment("user1", "bacaan1", "Child", "nonexistent"));
	}

	@Test
	void createComment_withParentFromDifferentBacaan_shouldThrow() {
		Comment parent = new Comment("user0", "bacaan2", "root", "Parent");
		parent.setId("parent-1");

		when(mockRepo.findById("parent-1")).thenReturn(Optional.of(parent));

		assertThrows(ResponseStatusException.class,
			() -> service.createComment("user1", "bacaan1", "Child", "parent-1"));
	}

	@Test
	void updateComment_shouldAllowAuthorAndPublishSanitizedContent() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		CommentUpdatedEvent result = service.updateComment("c1", "<b>Edited</b>", "user1", "USER");

		assertEquals("&lt;b&gt;Edited&lt;/b&gt;", result.commentContent());
		verify(mockRepo).updateContentById("c1", "&lt;b&gt;Edited&lt;/b&gt;");
		assertEquals(1, stubRabbit.published.size());
		assertEquals("yomu.comment.updated", stubRabbit.published.getFirst().routingKey());
		assertInstanceOf(CommentUpdatedEvent.class, stubRabbit.published.getFirst().payload());
	}

	@Test
	void updateComment_shouldAllowAdminToModerateAnotherUserComment() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		CommentUpdatedEvent result = service.updateComment("c1", "Cleaned", "admin-1", "ADMIN");

		assertEquals("Cleaned", result.commentContent());
		verify(mockRepo).updateContentById("c1", "Cleaned");
	}

	@Test
	void updateComment_shouldRejectNonOwnerAndNonAdmin() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		ResponseStatusException exception = assertThrows(
			ResponseStatusException.class,
			() -> service.updateComment("c1", "Edited", "user2", "USER")
		);

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
		verify(mockRepo, never()).updateContentById(anyString(), anyString());
	}

	@Test
	void updateComment_withoutAuthenticatedActor_shouldReject() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		ResponseStatusException exception = assertThrows(
			ResponseStatusException.class,
			() -> service.updateComment("c1", "Edited")
		);

		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
		verify(mockRepo, never()).updateContentById(anyString(), anyString());
	}

	@Test
	void addReaction_shouldIncrementCounter() {
		Comment comment = new Comment("user1", "bacaan1", "content");
		comment.setId("c1");
		comment.setUpvotes(0);

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		service.addReaction("c1", "user2", "upvote");

		verify(mockRepo, times(1)).addReaction("c1", "user2", "upvote");
	}

	@Test
	void addReaction_withMultipleTypes() {
		Comment comment = new Comment("user1", "bacaan1", "content");
		comment.setId("c1");

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		service.addReaction("c1", "user2", "heart");
		service.addReaction("c1", "user2", "laugh");
		service.addReaction("c1", "user2", "surprise");

		verify(mockRepo, times(1)).addReaction("c1", "user2", "heart");
		verify(mockRepo, times(1)).addReaction("c1", "user2", "laugh");
		verify(mockRepo, times(1)).addReaction("c1", "user2", "surprise");
	}

	@Test
	void addReaction_withNonExistentComment_shouldThrow() {
		when(mockRepo.findById("nonexistent")).thenReturn(Optional.empty());

		assertThrows(ResponseStatusException.class, () -> service.addReaction("nonexistent", "user2", "upvote"));
	}

	@Test
	void deleteComment_shouldAllowAuthorAndPublishEvent() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		CommentDeletedEvent result = service.deleteComment("c1", "user1", "USER");

		assertEquals("c1", result.commentId());
		verify(mockRepo).deleteById("c1");
		assertEquals(1, stubRabbit.published.size());
		assertEquals("yomu.comment.deleted", stubRabbit.published.getFirst().routingKey());
		assertInstanceOf(CommentDeletedEvent.class, stubRabbit.published.getFirst().payload());
	}

	@Test
	void deleteComment_shouldAllowAdminToDeleteAnotherUserComment() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		CommentDeletedEvent result = service.deleteComment("c1", "admin-1", "ADMIN");

		assertEquals("c1", result.commentId());
		verify(mockRepo).deleteById("c1");
		assertEquals("yomu.comment.deleted", stubRabbit.published.getFirst().routingKey());
	}

	@Test
	void deleteComment_shouldRejectNonOwnerAndNonAdmin() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		ResponseStatusException exception = assertThrows(
			ResponseStatusException.class,
			() -> service.deleteComment("c1", "user2", "USER")
		);

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
		verify(mockRepo, never()).deleteById(anyString());
	}

	@Test
	void deleteComment_withoutAuthenticatedActor_shouldReject() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		ResponseStatusException exception = assertThrows(
			ResponseStatusException.class,
			() -> service.deleteComment("c1")
		);

		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
		verify(mockRepo, never()).deleteById(anyString());
	}

	@Test
	void getComment_shouldReturnCommentResponse() {
		Comment comment = new Comment("user1", "bacaan1", "Test content");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));
		comment.setUpvotes(5);
		comment.setDownvotes(1);
		comment.setReactionThumbsUp(3);

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		CommentResponse result = service.getComment("c1");

		assertNotNull(result);
		assertEquals("c1", result.id());
		assertEquals("user1", result.userId());
		assertEquals("Test content", result.content());
		assertEquals(5, result.upvotes());
		assertEquals(1, result.downvotes());
		assertEquals(3, result.thumbsUp());
	}

	@Test
	void getComment_shouldResolveAuthorUsername() {
		Comment comment = new Comment("user1", "bacaan1", "Test content");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));
		service = new CommentServiceImpl(
				mockRepo,
				stubRabbit,
				fixedClock,
				meterRegistry,
				userId -> Optional.of(new CommentAuthorProfile("tirta.rendy", "Tirta Rendy"))
		);

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		CommentResponse result = service.getComment("c1");

		assertEquals("tirta.rendy", result.username());
		assertEquals("Tirta Rendy", result.displayName());
	}

	@Test
	void getComment_withNonExistentComment_shouldThrow() {
		when(mockRepo.findById("nonexistent")).thenReturn(Optional.empty());

		assertThrows(ResponseStatusException.class, () -> service.getComment("nonexistent"));
	}

	@Test
	void listComments_shouldReturnAllComments() {
		Comment c1 = new Comment("user1", "bacaan1", "Content 1");
		c1.setId("c1");
		c1.setCreatedAt(LocalDateTime.now(fixedClock));

		Comment c2 = new Comment("user2", "bacaan1", "Content 2");
		c2.setId("c2");
		c2.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findAll()).thenReturn(List.of(c1, c2));

		List<CommentResponse> results = service.listComments(null);

		assertEquals(2, results.size());
		assertEquals("c1", results.get(0).id());
		assertEquals("c2", results.get(1).id());
	}

	@Test
	void listComments_filterByBacaanId_shouldReturnFilteredComments() {
		Comment c1 = new Comment("user1", "bacaan1", "Content 1");
		c1.setId("c1");
		c1.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findByBacaanId("bacaan1")).thenReturn(List.of(c1));

		List<CommentResponse> results = service.listComments("bacaan1");

		assertEquals(1, results.size());
		assertEquals("bacaan1", results.get(0).bacaanId());
	}

	@Test
	void listComments_withEmptyBacaanId_shouldReturnAll() {
		Comment c1 = new Comment("user1", "bacaan1", "Content 1");
		c1.setId("c1");
		c1.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findAll()).thenReturn(List.of(c1));

		List<CommentResponse> results = service.listComments("");

		assertEquals(1, results.size());
	}

	@Test
	void listCommentsTree_shouldBuildTreeStructure() {
		Comment root = new Comment("user1", "bacaan1", "root", "Root comment");
		root.setId("root-1");
		root.setCreatedAt(LocalDateTime.now(fixedClock));

		Comment child = new Comment("user2", "bacaan1", "root-1", "Child comment");
		child.setId("child-1");
		child.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findAll()).thenReturn(List.of(root, child));

		List<CommentTreeResponse> trees = service.listCommentsTree(null);

		assertEquals(1, trees.size());
		assertEquals("root-1", trees.getFirst().id());
		assertEquals(1, trees.getFirst().replies().size());
		assertEquals("child-1", trees.getFirst().replies().getFirst().id());
		assertEquals("Child comment", trees.getFirst().replies().getFirst().content());
	}

	@Test
	void createComment_threeArgOverload_defaultsParentToRoot() {
		Comment saved = new Comment("user1", "bacaan1", "root", "Test");
		saved.setId("comment-1");
		saved.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.save(any(Comment.class))).thenReturn(saved);

		CommentCreatedEvent result = service.createComment("user1", "bacaan1", "Test");

		assertEquals("root", result.parentComment());
		verify(mockRepo).save(argThat(c -> "root".equals(c.getParentComment())));
	}

	@Test
	void updateComment_twoArgOverload_requiresAuthenticatedActor() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		ResponseStatusException exception = assertThrows(
			ResponseStatusException.class,
			() -> service.updateComment("c1", "Edited")
		);

		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
	}

	@Test
	void deleteComment_oneArgOverload_requiresAuthenticatedActor() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		ResponseStatusException exception = assertThrows(
			ResponseStatusException.class,
			() -> service.deleteComment("c1")
		);

		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
	}

	@Test
	void listCommentsTree_orphanChildPromotedToRootWhenParentMissing() {
		Comment orphan = new Comment("user2", "bacaan1", "missing-parent", "Orphan");
		orphan.setId("orphan-1");
		orphan.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findAll()).thenReturn(List.of(orphan));

		List<CommentTreeResponse> trees = service.listCommentsTree(null);

		assertEquals(1, trees.size());
		assertEquals("orphan-1", trees.getFirst().id());
		assertTrue(trees.getFirst().replies().isEmpty());
	}

	@Test
	void comment_constructorWithNullParent_defaultsToRoot() {
		Comment comment = new Comment("user1", "bacaan1", null, "content");
		assertEquals("root", comment.getParentComment());
	}

	@Test
	void comment_constructorWithBlankParent_defaultsToRoot() {
		Comment comment = new Comment("user1", "bacaan1", "   ", "content");
		assertEquals("root", comment.getParentComment());
	}

	@Test
	void listComments_whenRepositoryThrows_propagatesException() {
		when(mockRepo.findAll()).thenThrow(new RuntimeException("DB error"));

		assertThrows(RuntimeException.class, () -> service.listComments(null));
	}

	@Test
	void listComments_withBlankBacaanId_returnsAll() {
		when(mockRepo.findAll()).thenReturn(List.of());

		List<CommentResponse> results = service.listComments("   ");

		assertNotNull(results);
		verify(mockRepo).findAll();
	}

	@Test
	void deleteComment_withBlankUserId_shouldRejectWithUnauthorized() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		ResponseStatusException exception = assertThrows(
			ResponseStatusException.class,
			() -> service.deleteComment("c1", "  ", "USER")
		);

		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
	}

	@Test
	void listCommentsTree_whenRepositoryThrows_propagatesException() {
		when(mockRepo.findByBacaanId("bacaan1")).thenThrow(new RuntimeException("DB error"));

		assertThrows(RuntimeException.class, () -> service.listCommentsTree("bacaan1"));
	}

	@Test
	void updateComment_withBlankUserId_shouldRejectWithUnauthorized() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		ResponseStatusException exception = assertThrows(
			ResponseStatusException.class,
			() -> service.updateComment("c1", "Edited", "  ", "USER")
		);

		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
	}

	@Test
	void updateComment_withNullRole_authorIsAllowed() {
		Comment comment = new Comment("user1", "bacaan1", "root", "Original");
		comment.setId("c1");
		comment.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));
		when(mockRepo.updateContentById(anyString(), anyString())).thenReturn(1);

		CommentUpdatedEvent result = service.updateComment("c1", "Edited", "user1", null);

		assertNotNull(result);
		assertEquals("Edited", result.commentContent());
	}

	@Test
	void createComment_withNullContent_sanitizesToEmpty() {
		Comment saved = new Comment("user1", "bacaan1", "root", "");
		saved.setId("comment-1");
		saved.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.save(any(Comment.class))).thenReturn(saved);

		CommentCreatedEvent result = service.createComment("user1", "bacaan1", null, null);

		assertNotNull(result);
		verify(mockRepo).save(argThat(c -> "".equals(c.getContent())));
	}

	@Test
	void listCommentsTree_filterByBacaanId_shouldReturnFilteredTree() {
		Comment root = new Comment("user1", "bacaan1", "root", "Root");
		root.setId("root-1");
		root.setCreatedAt(LocalDateTime.now(fixedClock));

		when(mockRepo.findByBacaanId("bacaan1")).thenReturn(List.of(root));

		List<CommentTreeResponse> trees = service.listCommentsTree("bacaan1");

		assertEquals(1, trees.size());
		assertEquals("root-1", trees.getFirst().id());
	}

	@Test
	void addReaction_withNullReactionType_recordsUnknownMetric() {
		Comment comment = new Comment("user1", "bacaan1", "content");
		comment.setId("c1");

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		service.addReaction("c1", "user2", null);

		verify(mockRepo).addReaction("c1", "user2", null);
		assertEquals(1.0, meterRegistry.counter("yomu_forum_comment_reactions_total",
			"reaction_type", "unknown", "outcome", "success").count());
	}

	@Test
	void addReaction_withBlankReactionType_recordsUnknownMetric() {
		Comment comment = new Comment("user1", "bacaan1", "content");
		comment.setId("c1");

		when(mockRepo.findById("c1")).thenReturn(Optional.of(comment));

		service.addReaction("c1", "user2", "  ");

		verify(mockRepo).addReaction("c1", "user2", "  ");
		assertEquals(1.0, meterRegistry.counter("yomu_forum_comment_reactions_total",
			"reaction_type", "unknown", "outcome", "success").count());
	}

	static class RabbitTemplateStub extends org.springframework.amqp.rabbit.core.RabbitTemplate {
		record Published(String routingKey, Object payload) {}

		final java.util.List<Published> published = new java.util.ArrayList<>();

		@Override
		public void convertAndSend(String routingKey, Object message) {
			published.add(new Published(routingKey, message));
		}
	}
}
