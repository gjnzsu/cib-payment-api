package com.cib.payment.api.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public final class DecimalStringValidator implements ConstraintValidator<DecimalString, String> {
    private static final String DECIMAL_PATTERN = "^[0-9]+(\\.[0-9]{1,2})?$";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (!value.matches(DECIMAL_PATTERN)) {
            return false;
        }
        return new BigDecimal(value).compareTo(BigDecimal.ZERO) > 0;
    }
}
