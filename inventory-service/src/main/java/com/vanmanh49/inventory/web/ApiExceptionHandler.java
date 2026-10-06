package com.vanmanh49.inventory.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.vanmanh49.inventory.InsufficientStockException;
import com.vanmanh49.inventory.StockNotFoundException;
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

	@ExceptionHandler(StockNotFoundException.class)
	ProblemDetail notFound(StockNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(InsufficientStockException.class)
	ProblemDetail insufficientStock(InsufficientStockException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	// Two reservations racing for the same order reference end at the unique constraint.
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail constraintViolated(DataIntegrityViolationException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The reservation conflicts with an existing one");
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
