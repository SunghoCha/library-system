package msa.bookloan.application.port.out;

public interface InventoryPort {
    ReservationResult reserve(String sagaId, Long bookId);
    void cancelReservation(String sagaId, Long bookId);
}
