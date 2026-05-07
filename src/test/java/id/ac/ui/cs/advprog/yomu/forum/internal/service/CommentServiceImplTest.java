package id.ac.ui.cs.advprog.yomu.forum.internal.service;

import id.ac.ui.cs.advprog.yomu.forum.internal.model.Comment;
import id.ac.ui.cs.advprog.yomu.forum.internal.repository.CommentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.util.Optional;

import static org.mockito.Mockito.*;

class CommentServiceImplTest {

    @Test
    void addReaction_callsRepository() {
        CommentRepository repo = mock(CommentRepository.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        Clock clock = Clock.systemUTC();

        CommentServiceImpl service = new CommentServiceImpl(repo, rabbit, clock);

        Comment comment = new Comment("user1", "bacaan1", "content");
        comment.setId("c1");

        when(repo.findById("c1")).thenReturn(Optional.of(comment));

        service.addReaction("c1", "upvote");

        verify(repo, times(1)).addReaction("c1", "upvote");
    }
}
