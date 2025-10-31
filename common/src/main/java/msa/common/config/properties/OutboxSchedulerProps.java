package msa.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.outbox.scheduler")
public record OutboxSchedulerProps(
        int batchSize,
        int maxRetryCount,
        Duration grace,
        Duration staleTimeout,
        Duration lease         // 선점 임대시간(lease)
) {}
