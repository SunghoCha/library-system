package msa.bookloan.adapter.out.client;

import lombok.RequiredArgsConstructor;
import msa.bookloan.application.port.out.InventoryPort;
import msa.bookloan.application.port.out.response.ReservationResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryAdapter implements InventoryPort {

    // private final InventoryServiceClient inventoryServiceClient; // Feign Client 예시

    @Override
    public ReservationResult reserve(String sagaId, Long bookId) {
        System.out.println("재고 서비스에 도서 예약을 요청합니다. Saga ID: " + sagaId + ", Book ID: " + bookId);
        // 실제로는 Feign Client 등을 통해 재고 서비스의 API를 호출
        // return inventoryServiceClient.reserve(new ReserveRequest(sagaId, bookId));

        // 성공 시나리오 임시 구현
        return new ReservationResult(true, 1L);
    }

    @Override
    public void cancelReservation(String sagaId, Long bookId) {
        System.out.println("재고 서비스에 도서 예약 취소를 요청합니다. Saga ID: " + sagaId + ", Book ID: " + bookId);
        // 실제로는 Feign Client 등을 통해 재고 서비스의 예약 취소 API를 호출
        // inventoryServiceClient.cancel(new CancelRequest(sagaId, bookId));
    }
}