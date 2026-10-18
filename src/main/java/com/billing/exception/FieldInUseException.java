package com.billing.exception;

public class FieldInUseException extends RuntimeException {

    public FieldInUseException(String message) {
        super(message);
    }
}