package com.familymap.exception;

/**
 * Thrown for a bad email/password or refresh token (HTTP 401). The message is deliberately
 * generic so callers can't probe which emails are registered.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
