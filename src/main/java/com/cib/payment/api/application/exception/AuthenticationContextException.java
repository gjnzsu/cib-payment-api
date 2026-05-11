package com.cib.payment.api.application.exception;

public class AuthenticationContextException extends RuntimeException {
    public AuthenticationContextException(String message) {
        super(message);
    }
}
