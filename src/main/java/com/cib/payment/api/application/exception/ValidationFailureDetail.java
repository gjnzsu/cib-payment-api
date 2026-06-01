package com.cib.payment.api.application.exception;

public record ValidationFailureDetail(String field, String message) {}
