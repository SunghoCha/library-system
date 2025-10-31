package msa.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.inbox.scheduler")
public record InboxSchedulerProps(
        int batchSize,
        int maxRetryCount,
        java.time.Duration staleTimeout,
        java.time.Duration lease        // 선점 임대시간
) {}
