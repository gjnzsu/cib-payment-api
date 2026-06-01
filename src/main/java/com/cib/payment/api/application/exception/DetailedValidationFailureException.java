package com.cib.payment.api.application.exception;

import java.util.List;

public class DetailedValidationFailureException extends ValidationFailureException {
    private final List<ValidationFailureDetail> details;

    public DetailedValidationFailureException(String message, List<ValidationFailureDetail> details) {
        super(message);
        this.details = List.copyOf(details);
    }

    public List<ValidationFailureDetail> details() {
        return details;
    }
}
