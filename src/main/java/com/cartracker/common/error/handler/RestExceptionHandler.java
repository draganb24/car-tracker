package com.cartracker.common.error.handler;

import com.cartracker.common.error.exception.EntityNotFoundException;
import com.cartracker.common.error.exception.InvalidParamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

/**
 * Global REST exception handling. Replaces the per-controller
 * {@code @ExceptionHandler} methods so every controller returns a
 * consistent {@code {code, message}} body.
 */
@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<Object> handleEntityNotFoundException(EntityNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
        "code", ex.getEntityClass().getSimpleName() + ".notFound",
        "message", ex.getMessage()
    ));
  }

  @ExceptionHandler(InvalidParamException.class)
  public ResponseEntity<Object> handleInvalidParamException(InvalidParamException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
        "code", "InvalidParam",
        "message", ex.getMessage()
    ));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
        "code", "InternalError",
        "message", ex.getMessage()
    ));
  }
}
