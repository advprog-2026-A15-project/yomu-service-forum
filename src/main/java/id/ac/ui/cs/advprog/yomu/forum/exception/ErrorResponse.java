package id.ac.ui.cs.advprog.yomu.forum.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Standard error response untuk API.
 */
@Getter
@AllArgsConstructor
public class ErrorResponse {
    private String message;
    private String errorCode;
    private long timestamp;
    private int status;
}
