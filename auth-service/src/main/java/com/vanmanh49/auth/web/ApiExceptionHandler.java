package com.vanmanh49.auth.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.vanmanh49.auth.BadCredentialsException;
import com.vanmanh49.auth.UnauthenticatedException;
import com.vanmanh49.auth.UsernameTakenException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(UsernameTakenException.class)
	ProblemDetail usernameTaken(UsernameTakenException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	// Two registrations racing past the existence check end at the unique constraint.
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail constraintViolated(DataIntegrityViolationException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Username is already taken");
	}

	@ExceptionHandler({ BadCredentialsException.class, UnauthenticatedException.class })
	ProblemDetail unauthorized(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult()
			.getFieldErrors()
			.forEach((error) -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

}
