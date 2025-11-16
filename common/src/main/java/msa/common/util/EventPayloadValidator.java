package msa.common.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@RequiredArgsConstructor
public class EventPayloadValidator {

    private final Validator validator;

    public void validateOrThrow(Object payload) {
        if (payload == null) {
            throw new ConstraintViolationException("payload must not be null", Set.of());
        }

        Set<ConstraintViolation<Object>> violations = validator.validate(payload);
        if (violations.isEmpty()) return;

        String msg = firstViolationMessage(violations);
        throw new ConstraintViolationException("Invalid payload: " + msg, violations);
    }

    private static String firstViolationMessage(Set<? extends ConstraintViolation<?>> v) {
        ConstraintViolation<?> cv = v.iterator().next();
        return cv.getPropertyPath() + " " + cv.getMessage();
    }
}

