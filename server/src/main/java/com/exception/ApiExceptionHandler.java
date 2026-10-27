package com.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.io.IOException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(String status, String message) {}
    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError("ERROR", message));
    }
    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> badInput(Exception e) {
        return error(HttpStatus.BAD_REQUEST, e instanceof IllegalArgumentException && e.getMessage() != null
                ? e.getMessage() : "Dữ liệu yêu cầu không hợp lệ hoặc thiếu trường bắt buộc.");
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> missing(ResourceNotFoundException e) { return error(HttpStatus.NOT_FOUND, e.getMessage()); }
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> credentials() { return error(HttpStatus.UNAUTHORIZED, "Sai tên đăng nhập hoặc mật khẩu."); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden() { return error(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này."); }
    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    public ResponseEntity<ApiError> conflict(Exception e) {
        log.warn("Conflicting database write", e);
        return error(HttpStatus.CONFLICT, "Dữ liệu đã tồn tại hoặc đang được cập nhật. Hãy tải lại và thử lại.");
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> size() { return error(HttpStatus.PAYLOAD_TOO_LARGE, "File hoặc tổng dung lượng tải lên vượt giới hạn."); }
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiError> file(IOException e) { return error(HttpStatus.BAD_REQUEST, "Không đọc được file Excel. Kiểm tra lại định dạng và nội dung."); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unhandled backend error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Máy chủ gặp lỗi khi xử lý. Vui lòng thử lại sau.");
    }
}
