package msa.bookloan.domain.model;

public enum LoanStatus {
    LOANED,     // 대출 중
    RETURNED,   // 반납 완료
    OVERDUE,    // 연체 중
    CANCELED    // 대출 취소(예약 취소 등)
}
