package msa.inventory.application.service;

import lombok.RequiredArgsConstructor;
import msa.common.events.bookloan.saga.command.ReleaseInventoryCommand;
import msa.common.events.bookloan.saga.command.ReserveInventoryCommand;
import msa.inventory.adaptor.out.persistence.bookcopy.repository.BookCopyRepository;
import msa.inventory.application.service.exception.InventoryNotFoundException;
import msa.inventory.domain.model.BookCopy;
import msa.inventory.domain.model.BookCopyStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static msa.inventory.domain.model.BookCopyStatus.*;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final BookCopyRepository bookCopyRepository;

    /*
        bookId 로 재고 애그리게이트 조회

        재고 도메인 로직 수행 (예약 가능 여부 체크 + 수량 차감 / 예약등록)

        사가 reply outbox 기록 (성공/실패 둘 다)
     */
    @Transactional
    public void reserve(ReserveInventoryCommand payload) {
        Long bookId = payload.bookId();
        Long sagaId = payload.sagaId();
        Long commandId = payload.commandId();


        BookCopy bookCopy = bookCopyRepository.lockFirstAvailableForUpdate(bookId, AVAILABLE.name())
                .orElse(null);

        if (bookCopy == null) {}

    }

    @Transactional
    public void release(ReleaseInventoryCommand payload) {

    }
}
