package msa.bookcatalog.infra.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox.scheduler")
public record OutboxSchedulerProperties(
        int maxRetryCount,
        int batchSize,
        int gracePeriodMinutes,
        int staleTimeoutMinutes
) {}
