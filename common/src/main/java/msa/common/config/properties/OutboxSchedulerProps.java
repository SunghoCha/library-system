package msa.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.outbox.scheduler")
public record OutboxSchedulerProps(
        @DefaultValue("100") int batchSize,
        @DefaultValue("3") int maxRetryCount,
        @DefaultValue("5s") Duration grace, // 삭제 고민
        @DefaultValue("10m") Duration staleTimeout, // 삭제 고민
        @DefaultValue("30s") Duration lease      // 선점 임대시간(lease)
) {}
