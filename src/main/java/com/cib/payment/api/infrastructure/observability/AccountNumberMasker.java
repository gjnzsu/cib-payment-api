package com.cib.payment.api.infrastructure.observability;

import java.util.regex.Pattern;

public final class AccountNumberMasker {
    private static final Pattern EMAIL = Pattern.compile("^([^@]{1,64})@(.+)$");
    private static final Pattern HKID_LIKE = Pattern.compile("^[A-Z]{1,2}\\d{6}\\([0-9A]\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOBILE_LIKE = Pattern.compile("^\\+?\\d[\\d\\s-]{7,}$");
    private static final Pattern FI_XML_PAYLOAD = Pattern.compile(
            "(?is)<\\s*Document\\b.*(?:pacs\\.009|camt\\.056|camt\\.029).*<\\s*/\\s*Document\\s*>");
    private static final Pattern BIC_LINKED_ACCOUNT_REFERENCE = Pattern.compile(
            "^([A-Z0-9]{8,11}-[A-Z]{3}-)([A-Za-z0-9]{5,})$");
    private static final Pattern SIMULATED_CORRESPONDENT_ACCOUNT_REFERENCE = Pattern.compile(
            "^((?:nostro|vostro|loro)-[a-z]{3}-[a-z0-9]{8,11}-)([A-Za-z0-9]{5,})$",
            Pattern.CASE_INSENSITIVE);
    private static final String FI_XML_PAYLOAD_OMITTED = "[FI_XML_PAYLOAD_OMITTED]";

    private AccountNumberMasker() {
    }

    public static String mask(String accountNumber) {
        return maskAccount(accountNumber);
    }

    public static String maskSensitive(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var trimmed = value.trim();
        if (FI_XML_PAYLOAD.matcher(trimmed).find()) {
            return FI_XML_PAYLOAD_OMITTED;
        }
        var maskedFiReference = maskFiReference(trimmed);
        if (maskedFiReference != null) {
            return maskedFiReference;
        }
        if (HKID_LIKE.matcher(trimmed).matches()) {
            return trimmed.charAt(0) + "******(*)";
        }
        if (trimmed.startsWith("FPS-")) {
            return maskFpsProxy(trimmed);
        }
        if (trimmed.contains("@")) {
            return maskEmail(trimmed);
        }
        if (MOBILE_LIKE.matcher(trimmed).matches()) {
            return maskAccount(digitsOnly(trimmed));
        }
        return maskAccount(trimmed);
    }

    private static String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "";
        }
        if (accountNumber.length() <= 4) {
            return "*".repeat(accountNumber.length());
        }
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }

    private static String maskEmail(String value) {
        var matcher = EMAIL.matcher(value);
        if (!matcher.matches()) {
            return maskAccount(value);
        }
        var localPart = matcher.group(1);
        var domain = matcher.group(2);
        if (localPart.length() <= 2) {
            return "*".repeat(localPart.length()) + "@" + domain;
        }
        return localPart.charAt(0)
                + "*".repeat(localPart.length() - 2)
                + localPart.charAt(localPart.length() - 1)
                + "@"
                + domain;
    }

    private static String maskFpsProxy(String value) {
        if (value.length() <= 5) {
            return "*".repeat(value.length());
        }
        return value.charAt(0) + "*".repeat(Math.min(15, value.length() - 5)) + value.substring(value.length() - 4);
    }

    private static String maskFiReference(String value) {
        var simulatedAccount = SIMULATED_CORRESPONDENT_ACCOUNT_REFERENCE.matcher(value);
        if (simulatedAccount.matches()) {
            return simulatedAccount.group(1) + maskAccount(simulatedAccount.group(2));
        }

        var bicLinkedAccount = BIC_LINKED_ACCOUNT_REFERENCE.matcher(value);
        if (bicLinkedAccount.matches()) {
            return bicLinkedAccount.group(1) + maskAccount(bicLinkedAccount.group(2));
        }
        return null;
    }

    private static String digitsOnly(String value) {
        return value.replaceAll("\\D", "");
    }
}
