package com.cib.payment.api.api;

import com.cib.payment.api.api.dto.ErrorResponse;
import com.cib.payment.api.api.dto.ValidationErrorDetailResponse;
import com.cib.payment.api.application.exception.AuthorizationScopeException;
import com.cib.payment.api.application.exception.DownstreamProcessingException;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.SemanticPaymentException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final PaymentObservability observability;

    public GlobalExceptionHandler(PaymentObservability observability) {
        this.observability = observability;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        var details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .toList();
        observability.validationFailure("VALIDATION_ERROR", correlation(request));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class, ValidationFailureException.class})
    ResponseEntity<ErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        observability.validationFailure("VALIDATION_ERROR", correlation(request));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, List.of());
    }

    @ExceptionHandler(AuthorizationScopeException.class)
    ResponseEntity<ErrorResponse> handleAuthorizationScope(HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Required scope is missing", request, List.of());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment was not found", request, List.of());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ErrorResponse> handleIdempotencyConflict(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different request", request, List.of());
    }

    @ExceptionHandler(SemanticPaymentException.class)
    ResponseEntity<ErrorResponse> handleSemanticPayment(HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "SEMANTIC_PAYMENT_ERROR", "Payment instruction is not supported", request, List.of());
    }

    @ExceptionHandler(DownstreamProcessingException.class)
    ResponseEntity<ErrorResponse> handleDownstreamProcessing(HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "DOWNSTREAM_PROCESSING_ERROR", "Downstream processing failed", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected API failure", request, List.of());
    }

    private ValidationErrorDetailResponse toDetail(FieldError error) {
        return new ValidationErrorDetailResponse(error.getField(), error.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<ValidationErrorDetailResponse> details) {
        var body = new ErrorResponse(code, message, status.value(), correlationId(request), details);
        return ResponseEntity.status(status).body(body);
    }

    private String correlationId(HttpServletRequest request) {
        var value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME);
        return value == null ? null : value.toString();
    }

    private CorrelationId correlation(HttpServletRequest request) {
        var value = correlationId(request);
        return new CorrelationId(value == null ? "unknown" : value);
    }
}
