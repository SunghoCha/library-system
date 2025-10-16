package msa.bookloan.application.saga;

import lombok.RequiredArgsConstructor;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.infra.config.properties.SagaProps;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 나중에 서비스별로 타임아웃 분리해서 정책으로 하거나 외부변수로 뽑을지도?
@Component
@RequiredArgsConstructor
public final class SagaTimeouts {

    private final SagaProps props;

    public Duration stepTimeout(LoanSagaStep step) {
        SagaProps.Steps steps = props.steps();
        Duration duration = null;
        switch (step) {
            case MEMBER_CHECKING -> duration = steps != null ? steps.memberChecking() : null;
            case INVENTORY_RESERVING -> duration = steps != null ? steps.inventoryReserving() : null;
            case POINT_CHARGING -> duration = steps != null ? steps.pointCharging() : null;
            case SHIPPING_SCHEDULING -> duration = steps != null ? steps.shippingScheduling() : null;
            case INIT, FINISHED -> duration = Duration.ZERO;
            case SHIPPING_ACCEPTED ->
                    duration = (steps != null && steps.shippingAccepted() != null)
                            ? steps.shippingAccepted()
                            : Duration.ofMinutes(10);
        }
        return duration != null ? duration : defaultStepTimeout();

    }

    public Duration compensationTimeoutFor() {
        return props.defaultStepTimeout() != null ? defaultStepTimeout() : Duration.ofSeconds(30);
    }

    private Duration defaultStepTimeout() {
        return props.defaultStepTimeout() != null ? props.defaultStepTimeout()
                : Duration.ofSeconds(30);
    }
}
