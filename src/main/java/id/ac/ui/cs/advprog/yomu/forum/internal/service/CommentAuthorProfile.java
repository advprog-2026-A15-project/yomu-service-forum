package id.ac.ui.cs.advprog.yomu.forum.internal.service;

public record CommentAuthorProfile(
		String username,
		String displayName
) {
	public static CommentAuthorProfile empty() {
		return new CommentAuthorProfile(null, null);
	}
}
