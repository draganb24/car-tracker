package com.cartracker.common.error.exception;

/**
 * Thrown on invalid client input that maps to HTTP 400 (e.g. a required
 * query parameter is missing or a cohort lookup returns no data).
 */
public class InvalidParamException extends RuntimeException {

  public InvalidParamException(String message) {
    super(message);
  }
}
