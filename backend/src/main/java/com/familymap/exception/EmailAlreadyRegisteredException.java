package com.familymap.exception;

/** Thrown when registering with an email that already has an account (HTTP 409). */
public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("An account with this email already exists.");
    }
}
