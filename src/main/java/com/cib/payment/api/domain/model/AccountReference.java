package com.cib.payment.api.domain.model;

public record AccountReference(String bankCode, String accountNumber, String accountName) {
}
