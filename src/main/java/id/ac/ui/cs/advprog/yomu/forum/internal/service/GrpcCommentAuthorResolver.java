package id.ac.ui.cs.advprog.yomu.forum.internal.service;

import id.ac.ui.cs.advprog.yomu.shared.grpc.AuthServiceGrpcGrpc;
import id.ac.ui.cs.advprog.yomu.shared.grpc.GetUserByIdRequest;
import id.ac.ui.cs.advprog.yomu.shared.grpc.UserResponse;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GrpcCommentAuthorResolver implements CommentAuthorResolver {
	private static final Logger log = LoggerFactory.getLogger(GrpcCommentAuthorResolver.class);

	@GrpcClient("service-auth")
	private AuthServiceGrpcGrpc.AuthServiceGrpcBlockingStub authServiceStub;

	@Override
	public Optional<CommentAuthorProfile> resolve(String userId) {
		if (userId == null || userId.isBlank()) {
			return Optional.empty();
		}

		try {
			UserResponse response = authServiceStub.getUserById(
					GetUserByIdRequest.newBuilder()
							.setUserId(userId)
							.build()
			);
			return Optional.of(new CommentAuthorProfile(
					blankToNull(response.getUsername()),
					blankToNull(response.getDisplayName())
			));
		} catch (StatusRuntimeException exception) {
			log.debug("User profile lookup failed for comment author {}", userId, exception);
			return Optional.empty();
		}
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
