package me.jcloud.app.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import me.jcloud.app.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public Object handleBaseException(BaseException ex, HttpServletRequest request) {
        if (isHtmlRequest(request)) {
            return new ModelAndView("error/" + ex.getStatus().value(), "message", ex.getMessage());
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(now())
                .status(ex.getStatus().value())
                .error(ex.getStatus().getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> details = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                details.put(err.getField(), err.getDefaultMessage()));

        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .timestamp(now())
                .status(400)
                .message("Validation failed")
                .details(details)
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: ", request.getRequestURI(), ex);

        if (isHtmlRequest(request)) {
            return new ModelAndView("error/500");
        }

        return ResponseEntity.internalServerError().body(ErrorResponse.builder()
                .timestamp(now())
                .status(500)
                .message("An internal error occurred. Please try again later.")
                .path(request.getRequestURI())
                .build());
    }

    private boolean isHtmlRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
