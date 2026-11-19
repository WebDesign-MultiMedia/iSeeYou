package com.familymap.exception;

/** The caller is a member but isn't allowed to do this (e.g. delete someone else's place). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
