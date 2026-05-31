package id.ac.ui.cs.advprog.yomu.forum.internal.service;

import java.util.Optional;

@FunctionalInterface
public interface CommentAuthorResolver {
	Optional<CommentAuthorProfile> resolve(String userId);
}
