package com.FinAssist.FinAssist.transaction.controller;

import com.FinAssist.FinAssist.ask.service.AskServiceException;
import com.FinAssist.FinAssist.transaction.service.TransactionNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(TransactionNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(AskServiceException.class)
    public ResponseEntity<ApiError> handleAskServiceFailure(AskServiceException exception) {
        logger.warn("AI answer generation failed", exception);
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The AI service is currently unavailable. Please try again later.",
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> details = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                        (first, second) -> first
                ));
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> details = exception.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage(),
                        (first, second) -> first
                ));
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Request contains an invalid value" : exception.getMessage();
        String field = message.startsWith("minAmount") ? "minAmount"
                : message.startsWith("startDate") ? "startDate"
                : message.startsWith("page") || message.startsWith("size") ? "pagination"
                : "request";
        return response(HttpStatus.BAD_REQUEST, "Request contains invalid values", Map.of(field, message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        Class<?> requiredType = exception.getRequiredType();
        String detail = "has an invalid value";
        if (requiredType != null && requiredType.isEnum()) {
            String allowedValues = Arrays.stream(requiredType.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            detail = "must be one of: " + allowedValues;
        } else if (requiredType == LocalDateTime.class) {
            detail = "must use ISO local date-time format, for example 2026-10-26T22:00:00";
        } else if (requiredType == BigDecimal.class) {
            detail = "must be a valid decimal amount";
        } else if (requiredType == Integer.class || requiredType == int.class) {
            detail = "must be a whole number";
        }
        return response(
                HttpStatus.BAD_REQUEST,
                "Query parameter has an invalid value",
                Map.of(exception.getName(), detail)
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        InvalidFormatException invalidFormatException = findInvalidFormatException(exception);
        if (invalidFormatException != null) {
            Class<?> targetType = invalidFormatException.getTargetType();
            if (targetType != null && targetType.isEnum()) {
                String allowedValues = Arrays.stream(targetType.getEnumConstants())
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                return response(
                        HttpStatus.BAD_REQUEST,
                        "Request body contains an invalid value",
                        Map.of("requestBody", "enum value must be one of: " + allowedValues)
                );
            }
            if (targetType == LocalDateTime.class) {
                return response(
                        HttpStatus.BAD_REQUEST,
                        "Request body contains an invalid value",
                        Map.of("requestBody",
                                "date-time values must use ISO local date-time format, for example 2026-10-26T22:00:00")
                );
            }
            return response(
                    HttpStatus.BAD_REQUEST,
                    "Request body contains an invalid value",
                    Map.of("requestBody", "contains a value with an invalid format")
            );
        }
        return response(
                HttpStatus.BAD_REQUEST,
                "Request body is malformed or contains invalid values",
                Map.of("requestBody", "must contain valid JSON with correctly formatted field values")
        );
    }

    private InvalidFormatException findInvalidFormatException(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidFormatException invalidFormatException) {
                return invalidFormatException;
            }
        }
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception exception) {
        logger.error("Unexpected error while handling an API request", exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                Map.of()
        );
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, Map<String, String> details) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message, details));
    }
}
