package msa.bookloan.domain.saga;

public enum  LoanSagaStep {
    INIT,
    MEMBER_CHECKING,
    INVENTORY_RESERVING,
    CANCEL_REQUESTED,       // 사용자 취소 접수
    INVENTORY_CANCELLING,
    FINISHED
}
