package msa.bookloan.application.port.out;

import msa.bookloan.application.port.out.response.ReservationResult;

public interface InventoryPort {
    ReservationResult reserve(String sagaId, Long bookId);
    void cancelReservation(String sagaId, Long bookId);
}
