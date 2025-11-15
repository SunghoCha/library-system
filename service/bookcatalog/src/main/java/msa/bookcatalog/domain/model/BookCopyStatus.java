package msa.bookcatalog.domain.model;

public enum BookCopyStatus {

    AVAILABLE,  // (재고 있음) - 대출 가능
    RESERVED,   // (예약됨) - 사가(Saga)가 진행 중이거나, 대출이 확정되어 보관 중
    LOANED
}
