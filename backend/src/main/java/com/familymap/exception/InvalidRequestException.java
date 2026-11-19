package com.familymap.exception;

/** A well-formed request that breaks a business rule bean validation can't express (HTTP 400). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
