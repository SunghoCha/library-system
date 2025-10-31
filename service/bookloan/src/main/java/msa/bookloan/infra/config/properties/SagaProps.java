package msa.bookloan.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.saga")
public record SagaProps(
        Duration overallTimeout,          // 전체 사가 타임아웃 (일단 안쓰다가 나중에 설정 고려)
        Duration defaultStepTimeout,      // 스텝 기본값
        Steps steps                       // 스텝별 개별값(없으면 기본값 사용)
) {
    public record Steps(
            Duration memberChecking,
            Duration inventoryReserving,
            Duration pointCharging,
            Duration shippingScheduling,
            Duration shippingAccepted
    ) {}
}
