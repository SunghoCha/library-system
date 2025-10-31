package msa.bookloan.adapter.out.messaging.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import msa.bookloan.adapter.in.messaging.inbox.scheduler.InboxPollingScheduler;
import msa.bookloan.adapter.out.persistence.inbox.entity.InboxEventRecord;
import msa.bookloan.adapter.out.persistence.inbox.repository.InboxEventRecordRepository;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaStatus;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReservedReply;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
public class SagaInboxProcessingIntegrationTest {

    @Autowired
    private InboxPollingScheduler scheduler;

    @Autowired
    private InboxEventRecordRepository inboxRepository;

    @Autowired
    private LoanSagaRepository sagaRepository;

    @Autowired
    private OutboxEventRecordRepository outboxRepository;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    private Snowflake snowflake;

    @MockBean
    Clock clock;

    private static final Long SAGA_ID= 123456789012345678L; // (임의의 Long 값)
    private static final Long LOAN_ID = 100L;
    private static final Long MEMBER_ID = 200L;
    private static final Long BOOK_ID = 300L;

    private static final Long INBOX_EVENT_ID = 1111L;
    private static final Long OUTBOX_ID = 2222L; // CommandOutboxRecorder가 사용할 ID
    private static final Long NEXT_COMMAND_ID = 3333L; // InventoryStepService가 생성할 Command ID
    private static final Long INBOX_RECORD_PK = 4444L;

    private static final Instant FIXED_INSTANT = Instant.parse("2000-10-31T10:00:00Z");

    @BeforeEach
    void setUp() {
        // Clock Mocking
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        // Snowflake Mocking
        // CommandOutboxRecorder가 upsertOutbox에 사용할 ID
        when(snowflake.nextId()).thenReturn(INBOX_RECORD_PK,OUTBOX_ID, NEXT_COMMAND_ID);
    }

    @Test
    @DisplayName("재고 예약 완료 이벤트 수신 시 사가 상태가 POINT_CHARGING으로 전이되고 Outbox에 커맨드가 저장된다")
    void shouldProcessInventoryReservedEventSuccessfully() throws Exception {
        // given
        // saga가 INVENTORY_RESERVING 상태에 있음
        LoanSaga saga = createAndSaveSaga(SAGA_ID, LOAN_ID, SagaStatus.PROCESSING, LoanSagaStep.INVENTORY_RESERVING);
        // 컨슈머로 INVENTORY_RESERVED 리플라이 와서 인박스에 저장한 상태
        String eventType = SagaReplyType.INVENTORY_RESERVED.getValue();
//        InventoryReservedReply reply = new InventoryReservedReply(INBOX_EVENT_ID, SAGA_ID, 99L, 0L, BOOK_ID, 88L);
//        createAndSaveInboxEvent(INBOX_EVENT_ID, eventType, reply, InboxEventRecordStatus.NEW);

        assertThat(outboxRepository.count()).isZero();
        // when
        // TODO : 미완성 테스트

    }

    private LoanSaga createAndSaveSaga(Long sagaId, Long loanId, SagaStatus status, LoanSagaStep step) {
        LoanSaga saga = LoanSaga.builder()
                .sagaId(sagaId)
                .loanId(loanId)
                .memberId(MEMBER_ID)
                .bookId(BOOK_ID)
                .aggregateVersion(0L)
                .triggerEventId(999L)
                .status(status)
                .currentStep(step)
                .stepDeadlineAt(LocalDateTime.now(clock).plusMinutes(5))
                .build();
        return sagaRepository.saveAndFlush(saga);
    }

    private InboxEventRecord createAndSaveInboxEvent(Long eventId, String eventType, Object payload, InboxEventRecordStatus status) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(payload);

        InboxEventRecord record = InboxEventRecord.builder()
                .id(snowflake.nextId())
                .eventId(eventId)
                .eventType(eventType)
                .payload(payloadJson)
                .inboxEventRecordStatus(status) // PENDING
                .aggregateId(SAGA_ID)
                .build();
        return inboxRepository.saveAndFlush(record);
    }
}
