package com.wonjune.backweb.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
		return build(ex.getStatus(), ex.getMessage(), request, null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
				.toList();
		return build(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다", request, fieldErrors);
	}

	/**
	 * Last-resort handler. Without it an unexpected exception escapes the dispatcher, the
	 * container re-dispatches to /error, and the security filter chain (registered for the
	 * ERROR dispatch type too) rejects that anonymous request -- so a server-side bug shows
	 * up to the caller as a misleading 401 instead of a 500.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다", request, null);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request,
			List<ErrorResponse.FieldError> fieldErrors) {
		ErrorResponse body = new ErrorResponse(
				LocalDateTime.now(), status.value(), status.getReasonPhrase(), message,
				request.getRequestURI(), fieldErrors
		);
		return ResponseEntity.status(status).body(body);
	}

}
