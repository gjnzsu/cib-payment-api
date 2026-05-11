package com.cib.payment.api.application.exception;

public class DownstreamProcessingException extends RuntimeException {
    public DownstreamProcessingException(String message) {
        super(message);
    }
}
