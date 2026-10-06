package com.vanmanh49.order.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.vanmanh49.order.OrderAlreadyCancelledException;
import com.vanmanh49.order.OrderNotFoundException;
import com.vanmanh49.order.UnauthenticatedException;
import com.vanmanh49.order.client.DependencyUnavailableException;
import com.vanmanh49.order.client.InsufficientStockException;
import com.vanmanh49.order.client.ProductNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(UnauthenticatedException.class)
	ProblemDetail unauthenticated(UnauthenticatedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	@ExceptionHandler(OrderNotFoundException.class)
	ProblemDetail notFound(OrderNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler({ InsufficientStockException.class, OrderAlreadyCancelledException.class })
	ProblemDetail conflict(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	// Another request changed the order first, for example a second cancel.
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"The order was changed by another request. Reload it and try again.");
	}

	@ExceptionHandler(ProductNotFoundException.class)
	ProblemDetail unknownProduct(ProductNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
	}

	@ExceptionHandler(DependencyUnavailableException.class)
	ProblemDetail dependencyUnavailable(DependencyUnavailableException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
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
