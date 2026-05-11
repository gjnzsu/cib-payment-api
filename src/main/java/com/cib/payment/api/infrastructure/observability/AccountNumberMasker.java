package com.cib.payment.api.infrastructure.observability;

public final class AccountNumberMasker {
    private AccountNumberMasker() {
    }

    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "";
        }
        if (accountNumber.length() <= 4) {
            return "*".repeat(accountNumber.length());
        }
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }
}
