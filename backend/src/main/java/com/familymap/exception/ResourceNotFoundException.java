package com.familymap.exception;

/** Thrown when a requested entity doesn't exist or isn't visible to the caller (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
