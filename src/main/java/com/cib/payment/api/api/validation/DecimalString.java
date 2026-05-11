package com.cib.payment.api.api.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@Constraint(validatedBy = DecimalStringValidator.class)
public @interface DecimalString {
    String message() default "must be a positive decimal string with at most two fractional digits";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
