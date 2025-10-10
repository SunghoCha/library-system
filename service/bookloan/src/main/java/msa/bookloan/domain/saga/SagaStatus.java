package msa.bookloan.domain.saga;

public enum SagaStatus {
    STARTED,        // 최초 생성
    PROCESSING,     // 중간 단계 진행 중
    COMPLETED,      // 성공 종료
    COMPENSATING,   // 보상 트랜잭션 진행 중
    FAILED
}
