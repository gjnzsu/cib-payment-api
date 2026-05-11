package com.cib.payment.api.application.exception;

public class AuthorizationScopeException extends RuntimeException {
    public AuthorizationScopeException(String message) {
        super(message);
    }
}
