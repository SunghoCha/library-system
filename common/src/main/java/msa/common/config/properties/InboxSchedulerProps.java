package msa.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.inbox.scheduler")
public record InboxSchedulerProps(
        @DefaultValue("100") int batchSize,
        @DefaultValue("3") int maxRetryCount,
        @DefaultValue("30s") Duration lease
) {}
