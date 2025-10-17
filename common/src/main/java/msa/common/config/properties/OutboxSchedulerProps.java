package msa.common.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox.scheduler")
public record OutboxSchedulerProps(
        @Min(0) int maxRetryCount,        // FAILED 재시도 상한
        @Min(1) int batchSize,            // 한 번에 선점할 개수
        @Min(0) int gracePeriodMinutes,   // NEW 발생 후 바로 집지 않기 위한 유예(grace)
        @Min(0) int staleTimeoutMinutes,  // 오래 묶인 PUBLISHING 회수(stale)
        @Min(1) int leaseSeconds          // 선점 임대시간(lease)
) {}
