package id.ac.ui.cs.advprog.yomu.forum.internal.repository;

import id.ac.ui.cs.advprog.yomu.forum.internal.model.Comment;

import java.util.Optional;
import java.util.List;

public interface CommentRepository {

    Comment save(Comment comment);

    Optional<Comment> findById(String id);

    int updateContentById(String id, String content);

    int deleteById(String id);

    List<Comment> findAll();

    List<Comment> findByBacaanId(String bacaanId);
}

