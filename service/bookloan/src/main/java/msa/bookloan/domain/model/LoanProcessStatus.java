package msa.bookloan.domain.model;

@Deprecated
public enum LoanProcessStatus {
    RECEIVED,           // 접수됨
    MEMBER_CHECKING,    // 멤버 검증 중
    INVENTORY_RESERVING,// 재고 예약 중
    COMPLETED,          // 사가 성공 종료(운영 상태로 넘어감)

    // 실패/종결
    REJECTED_MEMBER,
    REJECTED_OUT_OF_STOCK,
    FAILED,
    EXPIRED
}
