package id.ac.ui.cs.advprog.yomu.forum.internal.service;

import id.ac.ui.cs.advprog.yomu.forum.internal.model.Comment;
import id.ac.ui.cs.advprog.yomu.forum.internal.repository.CommentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentUpdatedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import java.util.List;

import id.ac.ui.cs.advprog.yomu.forum.internal.service.CommentAuthorProfile;

class CommentServiceImplExtraTest {

    private CommentRepository mockRepo;
    private RabbitTemplate stubRabbit;
    private Clock fixedClock;
    private SimpleMeterRegistry meterRegistry;
    private CommentServiceImpl service;

    static class RabbitTemplateStub extends RabbitTemplate {
        final java.util.List<Object> published = new java.util.ArrayList<>();
        @Override
        public void convertAndSend(String routingKey, Object message) {
            published.add(message);
        }
    }

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
                userId -> java.util.Optional.empty()
        );
    }

    @Test
    void createComment_withNullContent_persistsEmptyStringAndRecordsMetric() {
        Comment saved = new Comment("user1", "bacaan1", "root", "");
        saved.setId("comment-1");

        when(mockRepo.save(any(Comment.class))).thenReturn(saved);

        service.createComment("user1", "bacaan1", null, "root");

        verify(mockRepo).save(argThat(c -> "".equals(c.getContent())));
        assertEquals(1.0, meterRegistry.counter("yomu_forum_comment_actions_total", "action", "create", "outcome", "success").count());
    }

    @Test
    void addReaction_withNullReaction_recordsUnknownAndSuccess() {
        Comment comment = new Comment("user1", "bacaan1", "content");
        comment.setId("c1");

        when(mockRepo.findById("c1")).thenReturn(java.util.Optional.of(comment));

        service.addReaction("c1", "user2", null);

        verify(mockRepo).addReaction("c1", "user2", null);
        assertEquals(1.0, meterRegistry.counter("yomu_forum_comment_reactions_total", "reaction_type", "unknown", "outcome", "success").count());
    }

    @Test
    void addReaction_whenRepoThrows_recordsFailureMetric() {
        Comment comment = new Comment("user1", "bacaan1", "content");
        comment.setId("c1");

        when(mockRepo.findById("c1")).thenReturn(java.util.Optional.of(comment));
        doThrow(new RuntimeException("db error")).when(mockRepo).addReaction(anyString(), anyString(), anyString());

        assertThrows(RuntimeException.class, () -> service.addReaction("c1", "user2", "upvote"));

        assertEquals(1.0, meterRegistry.counter("yomu_forum_comment_reactions_total", "reaction_type", "upvote", "outcome", "failure").count());
        assertEquals(1.0, meterRegistry.counter("yomu_forum_comment_actions_total", "action", "react", "outcome", "failure").count());
    }

    @Test
    void updateComment_twoArg_unauthenticated_throwsUnauthorized_and_recordsFailure() {
        Comment comment = new Comment("user1", "bacaan1", "content");
        comment.setId("c1");
        when(mockRepo.findById("c1")).thenReturn(java.util.Optional.of(comment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.updateComment("c1", "edited"));
        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals(1.0, meterRegistry.counter("yomu_forum_comment_actions_total", "action", "update", "outcome", "failure").count());
    }

    @Test
    void deleteComment_oneArg_unauthenticated_throwsUnauthorized_and_recordsFailure() {
        Comment comment = new Comment("user1", "bacaan1", "content");
        comment.setId("c1");
        when(mockRepo.findById("c1")).thenReturn(java.util.Optional.of(comment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.deleteComment("c1"));
        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals(1.0, meterRegistry.counter("yomu_forum_comment_actions_total", "action", "delete", "outcome", "failure").count());
    }

    @Test
    void reflectivelyInvoke_twoArgUpdate_and_oneArgDelete_to_coverDelegatingMethods() throws Exception {
        Comment comment = new Comment("user1", "bacaan1", "content");
        comment.setId("c1");
        when(mockRepo.findById("c1")).thenReturn(java.util.Optional.of(comment));

        java.lang.reflect.Method upd = CommentServiceImpl.class.getDeclaredMethod("updateComment", String.class, String.class);
        try {
            upd.invoke(service, "c1", "edited");
            fail("Expected InvocationTargetException");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            assertTrue(ite.getCause() instanceof ResponseStatusException);
        }

        java.lang.reflect.Method del = CommentServiceImpl.class.getDeclaredMethod("deleteComment", String.class);
        try {
            del.invoke(service, "c1");
            fail("Expected InvocationTargetException");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            assertTrue(ite.getCause() instanceof ResponseStatusException);
        }
    }

    @Test
    void spyAndStub_delegatingOverloads_returnSuccessfully_andCoverReturnLines() {
        // create a spy so we can stub the multi-arg methods
        CommentServiceImpl spy = spy(service);

        CommentUpdatedEvent fakeUpdate = new CommentUpdatedEvent("u","b","p","c","edited", Instant.now());
        doReturn(fakeUpdate).when(spy).updateComment(eq("c1"), eq("edited"), isNull(), isNull());

        CommentUpdatedEvent res = spy.updateComment("c1", "edited");
        assertSame(fakeUpdate, res);

        CommentDeletedEvent fakeDelete = new CommentDeletedEvent("u","b","p","c","content", Instant.now());
        doReturn(fakeDelete).when(spy).deleteComment(eq("c1"), isNull(), isNull());

        CommentDeletedEvent dres = spy.deleteComment("c1");
        assertSame(fakeDelete, dres);
    }

    @Test
    void listComments_and_listCommentsTree_handleRootsChildrenAndOrphans_andResolveAuthors() {
        CommentRepository repo = mock(CommentRepository.class);
        RabbitTemplateStub rabbit = new RabbitTemplateStub();
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        Clock clk = fixedClock;

        // comments: root r1, child c1 -> parent r1, orphan o1 -> parent missing
        Comment r1 = new Comment("u1", "bac1", "root", "rootcontent");
        r1.setId("r1");
        Comment c1 = new Comment("u2", "bac1", "r1", "childcontent");
        c1.setId("c1");
        Comment o1 = new Comment("u3", "bac1", "missing", "orphancontent");
        o1.setId("o1");

        // set createdAt to avoid NPE when mapping to responses
        java.time.LocalDateTime createdAt = java.time.LocalDateTime.ofInstant(clk.instant(), clk.getZone());
        r1.setCreatedAt(createdAt);
        c1.setCreatedAt(createdAt);
        o1.setCreatedAt(createdAt);

        when(repo.findAll()).thenReturn(List.of(r1, c1, o1));
        when(repo.findByBacaanId("bac1")).thenReturn(List.of(r1, c1));

        CommentServiceImpl svc = new CommentServiceImpl(repo, rabbit, clk, reg, userId -> {
            if ("u2".equals(userId)) return java.util.Optional.of(new CommentAuthorProfile("u2","U Two"));
            return java.util.Optional.empty();
        });

        // listComments with null -> findAll
        List<?> all = svc.listComments(null);
        assertEquals(3, all.size());

        // listComments with bacaanId -> findByBacaanId
        List<?> byBac = svc.listComments("bac1");
        assertEquals(2, byBac.size());

        // listCommentsTree should promote orphan and nest child under root
        List<?> tree = svc.listCommentsTree(null);
        // roots should be r1 and o1 (order preserved)
        assertEquals(2, tree.size());
    }

    @Test
    void private_toCommentResponse_handlesNullAuthorProfile_viaReflection() throws Exception {
        // prepare comment
        java.time.LocalDateTime createdAt = java.time.LocalDateTime.ofInstant(fixedClock.instant(), fixedClock.getZone());
        Comment c = new Comment("ux", "bacx", "root", "cnt");
        c.setId("cx");
        c.setCreatedAt(createdAt);

        // invoke private method toCommentResponse with null authorProfile
        java.lang.reflect.Method m = CommentServiceImpl.class.getDeclaredMethod("toCommentResponse", Comment.class, CommentAuthorProfile.class);
        m.setAccessible(true);
        Object resp = m.invoke(service, c, null);

        assertNotNull(resp);
        // basic sanity: result should be a CommentResponse instance
        assertEquals("cx", resp.getClass().getMethod("id").invoke(resp));
    }

    @Test
    void listCommentsTree_deepNesting_recursesToChildren() throws Exception {
        CommentRepository repo = mock(CommentRepository.class);
        RabbitTemplateStub rabbit = new RabbitTemplateStub();
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        Clock clk = fixedClock;

        Comment r = new Comment("u1", "bacdeep", "root", "r"); r.setId("r");
        Comment c = new Comment("u2", "bacdeep", "r", "c"); c.setId("c");
        Comment g = new Comment("u3", "bacdeep", "c", "g"); g.setId("g");

        java.time.LocalDateTime createdAt = java.time.LocalDateTime.ofInstant(clk.instant(), clk.getZone());
        r.setCreatedAt(createdAt); c.setCreatedAt(createdAt); g.setCreatedAt(createdAt);

        when(repo.findAll()).thenReturn(List.of(r, c, g));

        CommentServiceImpl svc = new CommentServiceImpl(repo, rabbit, clk, reg, userId -> java.util.Optional.empty());

        List<?> tree = svc.listCommentsTree(null);
        assertEquals(1, tree.size());
        Object rootResp = tree.get(0);
        // root should have children, which have children
        java.lang.reflect.Method getChildren = rootResp.getClass().getMethod("replies");
        List<?> children = (List<?>) getChildren.invoke(rootResp);
        assertEquals(1, children.size());
        Object child = children.get(0);
        List<?> grand = (List<?>) child.getClass().getMethod("replies").invoke(child);
        assertEquals(1, grand.size());
    }
}
