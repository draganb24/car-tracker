package com.cartracker.common.error.exception;

import lombok.Getter;

/**
 * Thrown when an entity lookup fails. Carries the entity class so the global
 * handler can produce a stable error code like "Listing.notFound".
 */
@Getter
public class EntityNotFoundException extends RuntimeException {

  private final Class<?> entityClass;

  public EntityNotFoundException(Class<?> entityClass, String message) {
    super(message);
    this.entityClass = entityClass;
  }

}
