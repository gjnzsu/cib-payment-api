package com.cib.payment.api.domain.model;

import java.util.Optional;

public record BeneficiaryIdentifier(
        Optional<String> accountNumber,
        String accountName,
        String participantIdentifier,
        String fpsProxyType,
        Optional<String> fpsProxyValue) {

    public BeneficiaryIdentifier {
        accountNumber = accountNumber == null ? Optional.empty() : accountNumber;
        fpsProxyValue = fpsProxyValue == null ? Optional.empty() : fpsProxyValue;
    }

    public static BeneficiaryIdentifier of(
            String accountNumber,
            String accountName,
            String participantIdentifier,
            String fpsProxyType,
            String fpsProxyValue) {
        return new BeneficiaryIdentifier(
                Optional.ofNullable(accountNumber),
                accountName,
                participantIdentifier,
                fpsProxyType,
                Optional.ofNullable(fpsProxyValue));
    }

    public static BeneficiaryIdentifier account(String accountNumber, String accountName, String participantIdentifier) {
        return of(accountNumber, accountName, participantIdentifier, null, null);
    }

    public static BeneficiaryIdentifier fpsProxy(String fpsProxyType, String fpsProxyValue, String accountName) {
        return fpsProxy(fpsProxyType, fpsProxyValue, accountName, null);
    }

    public static BeneficiaryIdentifier fpsProxy(
            String fpsProxyType,
            String fpsProxyValue,
            String accountName,
            String participantIdentifier) {
        return of(null, accountName, participantIdentifier, fpsProxyType, fpsProxyValue);
    }

    public boolean hasAccountOrProxy() {
        return accountNumber.filter(this::hasText).isPresent()
                || fpsProxyValue.filter(this::hasText).isPresent();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
