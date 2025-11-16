package msa.common.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import msa.common.util.EventPayloadValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class CommonValidationConfig {

    @Bean
    @ConditionalOnMissingBean(Validator.class)
    public Validator defaultValidator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Bean
    @ConditionalOnMissingBean(EventPayloadValidator.class)
    public EventPayloadValidator eventPayloadValidator(Validator validator) {
        return new EventPayloadValidator(validator);
    }

}
