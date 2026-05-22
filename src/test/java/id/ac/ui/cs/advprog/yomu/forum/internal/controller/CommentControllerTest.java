package id.ac.ui.cs.advprog.yomu.forum.internal.controller;

import id.ac.ui.cs.advprog.yomu.forum.internal.service.CommentResponse;
import id.ac.ui.cs.advprog.yomu.forum.internal.service.CommentService;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentCreatedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentDeletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentControllerTest {

    private CommentService commentService;
    private CommentController controller;

    @BeforeEach
    void setUp() {
        commentService = mock(CommentService.class);
        controller = new CommentController(commentService);
    }

    @Test
    void createCommentUsesAuthenticatedUserId() {
        CommentCreatedEvent event = new CommentCreatedEvent(
            "user-1",
            "bacaan-1",
            "root",
            "comment-1",
            "Test",
            Instant.now()
        );
        when(commentService.createComment("user-1", "bacaan-1", "Test", "root"))
            .thenReturn(event);

        var response = controller.createComment(
            new CreateCommentRequest("bacaan-1", "Test", "root"),
            auth("user-1")
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("user-1", response.getBody().userId());
    }

    @Test
    void addReactionUsesAuthenticatedUserId() {
        CommentResponse updated = new CommentResponse(
            "comment-1",
            "user-1",
            "bacaan-1",
            "root",
            "Content",
            Instant.now(),
            1,
            0,
            0,
            0,
            0,
            0,
            0
        );
        when(commentService.getComment("comment-1")).thenReturn(updated);

        var response = controller.addReaction(
            "comment-1",
            new ReactionRequest("upvote"),
            auth("user-2")
        );

        verify(commentService).addReaction("comment-1", "user-2", "upvote");
        assertEquals(updated, response.getBody());
    }

    @Test
    void getCommentsReturnsServiceData() {
        CommentResponse comment = new CommentResponse(
            "comment-1",
            "user-1",
            "bacaan-1",
            "root",
            "Content",
            Instant.now(),
            0,
            0,
            0,
            0,
            0,
            0,
            0
        );
        when(commentService.listComments("bacaan-1")).thenReturn(List.of(comment));

        List<CommentResponse> comments = controller.getComments("bacaan-1");

        assertEquals(1, comments.size());
        assertEquals("comment-1", comments.getFirst().id());
    }

    @Test
    void createCommentWithoutAuthenticationThrowsUnauthorized() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.createComment(
                new CreateCommentRequest("bacaan-1", "Test", "root"),
                null
            )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void updateCommentPassesUserIdAndStrippedAdminRole() {
        CommentUpdatedEvent event = new CommentUpdatedEvent(
            "user-1",
            "bacaan-1",
            "root",
            "comment-1",
            "Edited",
            Instant.now()
        );
        when(commentService.updateComment("comment-1", "Edited", "admin-1", "ADMIN"))
            .thenReturn(event);

        CommentUpdatedEvent result = controller.updateComment(
            "comment-1",
            new UpdateCommentRequest("Edited"),
            authWithRole("admin-1", "ADMIN")
        );

        verify(commentService).updateComment("comment-1", "Edited", "admin-1", "ADMIN");
        assertEquals("Edited", result.commentContent());
    }

    @Test
    void deleteCommentWithoutAuthenticationThrowsUnauthorized() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.deleteComment("comment-1", null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void deleteCommentPassesUserIdAndRoleToService() {
        CommentDeletedEvent event = new CommentDeletedEvent(
            "user-1",
            "bacaan-1",
            "root",
            "comment-1",
            "Content",
            Instant.now()
        );
        when(commentService.deleteComment("comment-1", "user-1", null))
            .thenReturn(event);

        CommentDeletedEvent result = controller.deleteComment("comment-1", auth("user-1"));

        verify(commentService).deleteComment("comment-1", "user-1", null);
        assertEquals("comment-1", result.commentId());
    }

    private UsernamePasswordAuthenticationToken auth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private UsernamePasswordAuthenticationToken authWithRole(String userId, String role) {
        return new UsernamePasswordAuthenticationToken(
            userId,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
