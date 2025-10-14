package msa.bookloan.domain.saga;

public enum SagaStatus {
    PROCESSING,        // 정상 진행 중(STARTED는 제거하고 이걸로 통일)
    CANCEL_REQUESTED,  // 취소(정상 전진 차단인데 그냥 바로 COMPENSATING로 넘어갈수도)
    COMPENSATING,      // 보상 진행 중(나중에 쓸 자리, 일단 안 써도 됨)
    COMPLETED,         // 성공 종료
    FAILED,            // 실패 종료(타임아웃/검증실패 포함)
    CANCELLED,         // 취소 완료(보상 끝)
    TIMED_OUT          // 타임아웃 종료(원하면 FAILED로 흡수 가능)
}
