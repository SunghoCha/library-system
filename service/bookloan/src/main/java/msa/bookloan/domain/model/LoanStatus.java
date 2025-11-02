package msa.bookloan.domain.model;

public enum LoanStatus {
    /*
        대출 요청이 접수된 초기 상태.
        사가 처리가 진행 중일 때도 이 상태를 유지
        사가 진행중일떄 BookLoan.currentSagaId에 Saga ID 할당됨
     */
    PENDING,

    /*
        사가 성공 후의 운영 상태 (Happy Path)
        사가가 성공적으로 완료되어, 책이 사용자에게 '대출 중'인 상태.
     */
    LOANED,


    /*
        사용자가 책을 '반납 완료'한 상태.
     */
    RETURNED,


    //(보류?) 반납 기한이 지난 '연체' 상태. 이걸 상태로 지정하는게 맞나? 아니면 대출기한으로 정해지는 부산물(?)같은건가
    //OVERDUE,

    /*
        사가 실패/종료 상태 (Unhappy Path)
        사가가 실패하여(재고 부족, 멤버 거절 등) '대출이 거절'된 상태.
     */
    FAILED,

    /*
        사가 처리 중 사용자가 '요청을 취소'한 상태.
     */
    CANCELLED
}
