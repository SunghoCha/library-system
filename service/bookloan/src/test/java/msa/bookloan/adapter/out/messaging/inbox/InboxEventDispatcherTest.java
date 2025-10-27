package msa.bookloan.adapter.out.messaging.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import msa.bookloan.adapter.out.persistence.inbox.entity.InboxEventRecord;
import msa.bookloan.adapter.out.persistence.inbox.repository.InboxEventRecordRepository;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.reply.SagaReplyType;
import msa.bookloan.application.saga.reply.member.MemberCheckedPayload;
import msa.bookloan.application.saga.reply.member.MemberCheckedReply;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaStatus;
import msa.common.domain.model.InboxSource;
import msa.common.events.inbox.dto.ConsumerRecordMetadata;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InboxEventDispatcherTest {

    @Autowired
    private InboxEventDispatcher inboxEventDispatcher;

    @Autowired
    private InboxEventRecordRepository inboxRepository;

    @Autowired
    private LoanSagaRepository sagaRepository;

    @Autowired
    private OutboxEventRecordRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String SAGA_ID = "test-saga-" + UUID.randomUUID();
    private static final Long INBOX_ID = 1L;
    private static final Long EVENT_ID = 12345L;
    private static final Long LOAN_ID = 99L;
    private static final Long MEMBER_ID = 88L;
    private static final Long BOOK_ID = 77L;
    private static final Long AGGREGATE_VERSION = 1L; // BookLoan의 @Version 값
    private static final Long TRIGGER_EVENT_ID = 111L; // 사가 시작 이벤트 ID
    private static final Long CAUSATION_COMMAND_ID = 222L; // MemberCheckedReply를 유발한 커맨드 ID
    private static final String WORKER_ID = "test-worker";
    private final LocalDateTime pickedAt = LocalDateTime.now().withNano(0);

    @BeforeEach
    void setUp() throws Exception {
        outboxRepository.deleteAllInBatch();
        inboxRepository.deleteAllInBatch();
        sagaRepository.deleteAllInBatch();

        createProcessingSaga(SAGA_ID, LOAN_ID, LoanSagaStep.MEMBER_CHECKING, AGGREGATE_VERSION);
        MemberCheckedReply reply = createMemberCheckedReply(EVENT_ID, SAGA_ID, false, CAUSATION_COMMAND_ID, AGGREGATE_VERSION);
        createProcessingInboxEvent(reply, SagaReplyType.MEMBER_CHECKED, WORKER_ID, pickedAt);
    }

    @Test
    @DisplayName("시나리오 1: 성공 경로 - T-Step과 T-Status가 원자적으로 커밋된다")
    void testProcessEvent_Success() {

        // when
        assertDoesNotThrow(() ->
                inboxEventDispatcher.processEvent(EVENT_ID, pickedAt)
        );

        // then
        // 1. 인박스(T-Status) 검증: PROCESSED로 변경, 토큰 정리
        InboxEventRecord processedEvent = inboxRepository.findById(INBOX_ID).orElseThrow();
        assertEquals(InboxEventRecordStatus.PROCESSED, processedEvent.getInboxEventRecordStatus());
        assertNull(processedEvent.getWorkerId());
        assertNull(processedEvent.getPickedAt());

        // 2. 사가(T-Step) 검증: 다음 단계(INVENTORY_RESERVING)로 전이
        LoanSaga advancedSaga = sagaRepository.findById(SAGA_ID).orElseThrow();
        assertEquals(LoanSagaStep.INVENTORY_RESERVING, advancedSaga.getCurrentStep());

        // 3. 아웃박스(T-Step) 검증: 다음 커맨드 발행
        var outboxEvents = outboxRepository.findAll();
        assertEquals(1, outboxEvents.size());
        assertEquals("ReserveInventoryCommand", outboxEvents.get(0).getEventType());
    }


    private LoanSaga createProcessingSaga(String sagaId, Long loanId, LoanSagaStep currentStep, Long aggregateVersion) {
        LoanSaga saga = LoanSaga.builder()
                .sagaId(sagaId)
                .loanId(loanId)
                .memberId(MEMBER_ID)
                .bookId(BOOK_ID)
                .aggregateVersion(aggregateVersion)
                .triggerEventId(TRIGGER_EVENT_ID)
                .status(SagaStatus.PROCESSING)
                .currentStep(currentStep)
                .build();

        return sagaRepository.saveAndFlush(saga);
    }

     // 선점 완료(PROCESSING) 상태의 인박스 이벤트 생성
    private Long createProcessingInboxEvent(Object payloadDto,
                                            SagaReplyType eventType,
                                            String workerId,
                                            LocalDateTime pickedAt) throws Exception {

        ConsumerRecordMetadata metadata = new ConsumerRecordMetadata(
                "test-topic", // topic
                0,            // partitionNo
                123L          // recordOffset
        );

        InboxEventRecord inboxEvent = InboxEventRecord.builder()
                .id(INBOX_ID)
                .eventId(EVENT_ID)
                .aggregateId(LOAN_ID)
                .aggregateVersion(AGGREGATE_VERSION)
                .eventType(eventType.getValue())
                .payload(objectMapper.writeValueAsString(payloadDto))
                .source(InboxSource.MEMBER)
                .inboxEventRecordStatus(InboxEventRecordStatus.PROCESSING)
                .workerId(workerId)
                .pickedAt(pickedAt)
                .lastSeenAt(pickedAt)
                .consumerRecordMetadata(metadata)
                .build();

        InboxEventRecord savedInbox = inboxRepository.saveAndFlush(inboxEvent);
        return savedInbox.getId();
    }

    private MemberCheckedReply createMemberCheckedReply(Long eventId, String sagaId, boolean blacklisted,
                                                        Long causationCommandId, Long sourceAggregateVersion) {

        MemberCheckedPayload payload = new MemberCheckedPayload(MEMBER_ID, blacklisted, null);

        return new MemberCheckedReply(
                eventId,
                sagaId,
                causationCommandId,
                sourceAggregateVersion,
                payload
        );
    }
}