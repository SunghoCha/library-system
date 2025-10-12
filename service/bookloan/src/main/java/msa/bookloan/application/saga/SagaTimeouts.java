package msa.bookloan.application.saga;

import java.time.Duration;

// 나중에 서비스별로 타임아웃 분리해서 정책으로 하거나 외부변수로 뽑을지도?
public final class SagaTimeouts {
    private SagaTimeouts() {}
    public static final Duration STEP_TIMEOUT = Duration.ofSeconds(2);
}
