package com.cib.payment.api.application.exception;

public class AdvisorScenarioNotFoundException extends RuntimeException {
    public AdvisorScenarioNotFoundException() {
        super("Advisor scenario was not found");
    }
}
