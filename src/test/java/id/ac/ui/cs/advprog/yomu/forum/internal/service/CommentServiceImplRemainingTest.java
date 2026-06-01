package id.ac.ui.cs.advprog.yomu.forum.internal.service;

import id.ac.ui.cs.advprog.yomu.shared.event.CommentCreatedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentDeletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentUpdatedEvent;
import id.ac.ui.cs.advprog.yomu.forum.internal.model.Comment;
import id.ac.ui.cs.advprog.yomu.forum.internal.repository.CommentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommentServiceImplRemainingTest {

    @Test
    void interfaceDefaultCreateComment_forwardsParentAsRoot() {
        CommentService svc = new CommentService() {
            @Override
            public CommentCreatedEvent createComment(String userId, String bacaanId, String commentContent, String parentComment) {
                assertEquals("root", parentComment);
                return null;
            }

            @Override public CommentUpdatedEvent updateComment(String commentId, String commentContent) { return null; }
            @Override public CommentUpdatedEvent updateComment(String commentId, String commentContent, String userId, String role) { return null; }
            @Override public CommentDeletedEvent deleteComment(String commentId) { return null; }
            @Override public CommentDeletedEvent deleteComment(String commentId, String userId, String role) { return null; }
            @Override public void addReaction(String commentId, String userId, String reactionType) { }
            @Override public List<CommentResponse> listComments(String bacaanId) { return List.of(); }
            @Override public List<CommentTreeResponse> listCommentsTree(String bacaanId) { return List.of(); }
            @Override public CommentResponse getComment(String commentId) { return null; }
        };

        // Should call default method which forwards to the 4-arg overload with "root"
        assertNull(svc.createComment("u","b","c"));
    }

    @Test
    void listComments_handlesNullAndNonNullBacaanId() {
        CommentRepository repo = mock(CommentRepository.class);
        RabbitTemplate rt = mock(RabbitTemplate.class);
        SimpleMeterRegistry meter = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        CommentAuthorResolver resolver = mock(CommentAuthorResolver.class);

        Comment c1 = new Comment("user1","bacaanA","root","one");
        c1.setId("c1");
        c1.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), clock.getZone()));

        when(repo.findAll()).thenReturn(List.of(c1));
        when(repo.findByBacaanId("bacaanA")).thenReturn(List.of(c1));
        when(resolver.resolve("user1")).thenReturn(Optional.empty());

        CommentServiceImpl svc = new CommentServiceImpl(repo, rt, clock, meter, resolver);

        List<CommentResponse> all = svc.listComments(null);
        assertEquals(1, all.size());

        List<CommentResponse> byBacaan = svc.listComments("bacaanA");
        assertEquals(1, byBacaan.size());
        assertEquals("bacaanA", byBacaan.get(0).bacaanId());
    }

    @Test
    void listCommentsTree_buildsRootsAndChildRelationships_includingOrphans() {
        CommentRepository repo = mock(CommentRepository.class);
        RabbitTemplate rt = mock(RabbitTemplate.class);
        SimpleMeterRegistry meter = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        CommentAuthorResolver resolver = mock(CommentAuthorResolver.class);

        Comment parent = new Comment("u1","B1","root","p");
        parent.setId("p1");
        parent.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), clock.getZone()));

        Comment child = new Comment("u2","B1","p1","c");
        child.setId("c2");
        child.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), clock.getZone()));

        Comment orphan = new Comment("u3","B1","missing","o");
        orphan.setId("o3");
        orphan.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), clock.getZone()));

        when(repo.findAll()).thenReturn(List.of(parent, child, orphan));
        when(resolver.resolve("u1")).thenReturn(Optional.empty());
        when(resolver.resolve("u2")).thenReturn(Optional.empty());
        when(resolver.resolve("u3")).thenReturn(Optional.empty());

        CommentServiceImpl svc = new CommentServiceImpl(repo, rt, clock, meter, resolver);

        List<CommentTreeResponse> roots = svc.listCommentsTree(null);
        // parent and orphan should be roots
        assertEquals(2, roots.size());

        CommentTreeResponse rootP = roots.stream().filter(r -> r.id().equals("p1")).findFirst().orElse(null);
        assertNotNull(rootP);
        assertEquals(1, rootP.replies().size());
        assertEquals("c2", rootP.replies().get(0).id());
    }

    @Test
    void listComments_whenRepoThrows_recordsFailure() {
        CommentRepository repo = mock(CommentRepository.class);
        RabbitTemplate rt = mock(RabbitTemplate.class);
        SimpleMeterRegistry meter = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        CommentAuthorResolver resolver = mock(CommentAuthorResolver.class);

        when(repo.findAll()).thenThrow(new RuntimeException("boom"));

        CommentServiceImpl svc = new CommentServiceImpl(repo, rt, clock, meter, resolver);
        assertThrows(RuntimeException.class, () -> svc.listComments(null));
    }

    @Test
    void listCommentsTree_whenRepoThrows_recordsFailure() {
        CommentRepository repo = mock(CommentRepository.class);
        RabbitTemplate rt = mock(RabbitTemplate.class);
        SimpleMeterRegistry meter = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        CommentAuthorResolver resolver = mock(CommentAuthorResolver.class);

        when(repo.findAll()).thenThrow(new RuntimeException("boom"));

        CommentServiceImpl svc = new CommentServiceImpl(repo, rt, clock, meter, resolver);
        assertThrows(RuntimeException.class, () -> svc.listCommentsTree(null));
    }

    @Test
    void listCommentsTree_filtersByBacaanId_and_usesAuthorProfiles() {
        CommentRepository repo = mock(CommentRepository.class);
        RabbitTemplate rt = mock(RabbitTemplate.class);
        SimpleMeterRegistry meter = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        CommentAuthorResolver resolver = mock(CommentAuthorResolver.class);

        Comment root = new Comment("alice","B2","root","hello");
        root.setId("r1");
        root.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), clock.getZone()));

        Comment child = new Comment("bob","B2","r1","reply");
        child.setId("c1");
        child.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), clock.getZone()));

        when(repo.findByBacaanId("B2")).thenReturn(List.of(root, child));
        when(resolver.resolve("alice")).thenReturn(Optional.of(new CommentAuthorProfile("alice","Alice")));
        when(resolver.resolve("bob")).thenReturn(Optional.empty());

        CommentServiceImpl svc = new CommentServiceImpl(repo, rt, clock, meter, resolver);

        List<CommentTreeResponse> roots = svc.listCommentsTree("B2");
        assertEquals(1, roots.size());
        CommentTreeResponse treeRoot = roots.get(0);
        assertEquals("alice", treeRoot.username());
        assertEquals("Alice", treeRoot.displayName());
        assertEquals(1, treeRoot.replies().size());
        assertEquals("c1", treeRoot.replies().get(0).id());
    }

    @Test
    void listCommentsTree_emptyRepository_returnsEmpty() {
        CommentRepository repo = mock(CommentRepository.class);
        RabbitTemplate rt = mock(RabbitTemplate.class);
        SimpleMeterRegistry meter = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        CommentAuthorResolver resolver = mock(CommentAuthorResolver.class);

        when(repo.findAll()).thenReturn(List.of());
        when(resolver.resolve("any")).thenReturn(Optional.empty());

        CommentServiceImpl svc = new CommentServiceImpl(repo, rt, clock, meter, resolver);

        List<CommentTreeResponse> roots = svc.listCommentsTree(null);
        assertTrue(roots.isEmpty());
    }
}
