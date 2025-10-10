package msa.bookloan.application.port.out.response;

public record ReservationResult(
        boolean success,
        Long reservationId // 재고 예약 ID
) {
}
