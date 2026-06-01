package id.ac.ui.cs.advprog.yomu.forum.internal.service;

import id.ac.ui.cs.advprog.yomu.shared.grpc.AuthServiceGrpcGrpc;
import id.ac.ui.cs.advprog.yomu.shared.grpc.GetUserByIdRequest;
import id.ac.ui.cs.advprog.yomu.shared.grpc.UserResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrpcCommentAuthorResolverTest {

    private GrpcCommentAuthorResolver resolver;
    private AuthServiceGrpcGrpc.AuthServiceGrpcBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        resolver = new GrpcCommentAuthorResolver();
        stub = mock(AuthServiceGrpcGrpc.AuthServiceGrpcBlockingStub.class);

        Field f = GrpcCommentAuthorResolver.class.getDeclaredField("authServiceStub");
        f.setAccessible(true);
        f.set(resolver, stub);
    }

    @Test
    void resolve_withNullOrBlankUserId_returnsEmpty() {
        assertEquals(Optional.empty(), resolver.resolve(null));
        assertEquals(Optional.empty(), resolver.resolve("  "));
    }

    @Test
    void resolve_whenGrpcReturnsProfile_mapsFieldsAndReturns() {
        UserResponse resp = UserResponse.newBuilder()
                .setUsername("tirta.rendy")
                .setDisplayName("Tirta Rendy")
                .build();
        when(stub.getUserById(any(GetUserByIdRequest.class))).thenReturn(resp);

        Optional<CommentAuthorProfile> result = resolver.resolve("user-1");

        assertTrue(result.isPresent());
        assertEquals("tirta.rendy", result.get().username());
        assertEquals("Tirta Rendy", result.get().displayName());
    }

    @Test
    void resolve_whenGrpcReturnsBlankFields_convertsToNulls() {
        UserResponse resp = UserResponse.newBuilder()
                .setUsername("")
                .setDisplayName("")
                .build();
        when(stub.getUserById(any(GetUserByIdRequest.class))).thenReturn(resp);

        Optional<CommentAuthorProfile> result = resolver.resolve("user-1");

        assertTrue(result.isPresent());
        assertNull(result.get().username());
        assertNull(result.get().displayName());
    }

    @Test
    void resolve_whenGrpcThrows_returnsEmpty() {
        when(stub.getUserById(any(GetUserByIdRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertEquals(Optional.empty(), resolver.resolve("user-1"));
    }
}
