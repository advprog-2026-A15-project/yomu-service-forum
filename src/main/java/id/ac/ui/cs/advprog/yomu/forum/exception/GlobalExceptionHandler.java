package id.ac.ui.cs.advprog.yomu.forum.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Global exception handler untuk service-forum.
 * Menangani authorization errors dan HTTP exceptions dengan response yang konsisten.
 * 
 * Authorization policy:
 * - POST /api/forum/comments: Semua user authenticated bisa membuat komentar
 * - PUT /api/forum/comments/{id}: Hanya pemilik komentar atau ADMIN yang bisa edit
 * - DELETE /api/forum/comments/{id}: Hanya pemilik komentar atau ADMIN yang bisa hapus
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse(
            "Akses ditolak. Anda tidak memiliki otorisasi untuk melakukan operasi ini.",
            "AUTHORIZATION_DENIED",
            Instant.now().toEpochMilli(),
            HttpStatus.FORBIDDEN.value()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        ErrorResponse error = new ErrorResponse(
            ex.getReason() != null ? ex.getReason() : "Error",
            "HTTP_ERROR",
            Instant.now().toEpochMilli(),
            ex.getStatusCode().value()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        ErrorResponse error = new ErrorResponse(
            "Terjadi kesalahan server.",
            "INTERNAL_ERROR",
            Instant.now().toEpochMilli(),
            HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
