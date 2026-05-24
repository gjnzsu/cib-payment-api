package com.cib.payment.api.domain.model;

import java.util.Optional;

public record BeneficiaryIdentifier(
        Optional<String> accountNumber,
        String accountName,
        String participantIdentifier,
        String fpsProxyType,
        Optional<String> fpsProxyValue) {

    public static BeneficiaryIdentifier account(String accountNumber, String accountName, String participantIdentifier) {
        return new BeneficiaryIdentifier(Optional.ofNullable(accountNumber), accountName, participantIdentifier, null, Optional.empty());
    }

    public static BeneficiaryIdentifier fpsProxy(String fpsProxyType, String fpsProxyValue, String accountName) {
        return new BeneficiaryIdentifier(Optional.empty(), accountName, null, fpsProxyType, Optional.ofNullable(fpsProxyValue));
    }

    public boolean hasAccountOrProxy() {
        return accountNumber.filter(this::hasText).isPresent()
                || fpsProxyValue.filter(this::hasText).isPresent();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
