package com.threeline.merchantwallet.common.web;

import com.threeline.merchantwallet.common.db.DatabaseErrorTranslator;
import com.threeline.merchantwallet.common.error.ErrorCode;
import com.threeline.merchantwallet.common.error.WalletException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final DatabaseErrorTranslator databaseErrors;

    public GlobalExceptionHandler(DatabaseErrorTranslator databaseErrors) {
        this.databaseErrors = databaseErrors;
    }

    @ExceptionHandler(WalletException.class)
    public ResponseEntity<ProblemDetail> handleWallet(WalletException ex) {
        log.info("Business error {}: {}", ex.errorCode(), ex.getMessage());
        ResponseEntity<ProblemDetail> response = respond(ex.errorCode(), ex.getMessage());
        ex.properties().forEach(response.getBody()::setProperty);
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleParameterValidation(HandlerMethodValidationException ex) {
        List<Map<String, String>> errors = ex.getAllErrors().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "One or more parameters are invalid", errors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex) {
        return respond(ErrorCode.VALIDATION_FAILED, "Required header '" + ex.getHeaderName() + "' is missing");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(ErrorCode.VALIDATION_FAILED, "Parameter '" + ex.getName() + "' has an invalid value");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {

        return respond(ErrorCode.MALFORMED_REQUEST, "Request body is missing or is not valid JSON for this endpoint");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoRoute(NoResourceFoundException ex) {
        return respond(ErrorCode.ROUTE_NOT_FOUND, "No endpoint for " + ex.getHttpMethod() + " /" + ex.getResourcePath());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ex.getMethod() + " is not supported here");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDatabase(DataAccessException ex) {
        return databaseErrors.translate(ex)
                .map(code -> {
                    log.warn("Database constraint mapped to {}: {}", code, databaseErrors.describe(ex));
                    return respond(code, code.defaultMessage());
                })
                .orElseGet(() -> {
                    log.error("Unexpected database error: {}", databaseErrors.describe(ex), ex);
                    return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
                });
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail) {
        return respond(code, detail, null);
    }

    private ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail, List<Map<String, String>> errors) {
        return ResponseEntity.status(code.httpStatus()).body(ProblemDetailFactory.create(code, detail, errors));
    }

    private static Map<String, String> toFieldError(MessageSourceResolvable error) {
        String field = error instanceof FieldError fe ? fe.getField()
                : (error.getCodes() != null && error.getCodes().length > 0 ? error.getCodes()[error.getCodes().length - 1] : "parameter");
        return Map.of("field", field, "message", String.valueOf(error.getDefaultMessage()));
    }
}
