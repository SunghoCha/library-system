package msa.bookloan.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saga.timeout")
public record LoanTimeoutProps(
        int batchSize,
        int leaseSecond
) {
}
