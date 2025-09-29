package msa.bookloan.infra.kafka.validator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class EventPayloadValidator {

    private final Validator validator;

    public <T> void validateOrThrow(T payload) {
        Set<ConstraintViolation<T>> validate = validator.validate(payload);
        if (!validate.isEmpty()) throw new ConstraintViolationException(validate);
    }
}
