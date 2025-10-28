package msa.bookloan.adapter.out.messaging.inbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import msa.bookloan.adapter.out.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.adapter.out.persistence.inbox.entity.InboxEventRecord;
import msa.bookloan.adapter.out.persistence.inbox.repository.InboxEventRecordRepository;
import msa.common.domain.model.InboxSource;
import msa.common.events.inbox.dto.ConsumerRecordMetadata;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.exception.BusinessNotRetryableException;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(InboxEventDispatcherIntegrationTest.TestHandlerConfiguration.class)
public class InboxEventDispatcherIntegrationTest {

    @Autowired
    private InboxEventDispatcher dispatcher;

    @Autowired
    private InboxEventRecordRepository recordRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Snowflake snowflake;

    @SpyBean(name = "testEventHandler")
    private InboxEventHandler<TestPayload> testEventHandler;

    @Autowired
    private InboxStatusMarker inboxStatusMarker;

    record TestPayload(String message) {
    }

    @TestConfiguration
    static class TestHandlerConfiguration {
        @Bean
        public InboxEventHandler<TestPayload> testEventHandler() {
            return new InboxEventHandler<>() {
                @Override
                public String eventType() {
                    return "TEST_EVENT"; // 이걸로 일단 생성자에서 map생성할 때 key가 null이 되지않도록 세팅
                }

                @Override
                public Class<TestPayload> payloadType() {
                    return TestPayload.class;
                }

                @Override
                public void handle(TestPayload payload) {
                    // 어차피 Mock으로 대체되므로 내용은 중요하지 않음
                }
            };
        }
    }

    private final String eventType = "TEST_EVENT";
    private final String leaseId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        recordRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("성공: 이벤트 처리 후 DB 상태가 PROCESSED로 변경됨")
    void processEventSuccessfully() throws JsonProcessingException {
        // given
        TestPayload payload = new TestPayload("Success");
        InboxEventRecord record = createAndSaveTestRecord(eventType, objectMapper.writeValueAsString(payload));
        Long eventId = record.getEventId();
        doNothing().when(testEventHandler).handle(any(TestPayload.class));
        // when
        dispatcher.processEvent(eventId, leaseId);

        // then
        InboxEventRecord processedRecord = recordRepository.findById(record.getId()).orElseThrow();

        assertThat(processedRecord.getInboxEventRecordStatus()).isEqualTo(InboxEventRecordStatus.PROCESSED);
        verify(testEventHandler).handle(any(TestPayload.class));
    }

    @Test
    @DisplayName("[핵심] DLT: 비재시도 예외 발생 시 T1은 롤백, T2(상태변경)는 커밋되어 DEAD_LETTER가 됨")
    void shouldMarkDeadLetterOnBusinessException() throws JsonProcessingException {
        // given
        String errMessage = "biz error";
        TestPayload payload = new TestPayload("Fail");
        InboxEventRecord record = createAndSaveTestRecord(eventType, objectMapper.writeValueAsString(payload));
        Long eventId = record.getEventId();
        doThrow(new BusinessNotRetryableException(errMessage)).when(testEventHandler).handle(any(TestPayload.class));

        // when
        assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                .isInstanceOf(BusinessNotRetryableException.class)
                .hasMessage(errMessage);

        // then
        InboxEventRecord dltRecord = recordRepository.findByEventId(eventId).orElseThrow();
        assertThat(dltRecord.getInboxEventRecordStatus()).isEqualTo(InboxEventRecordStatus.DEAD_LETTER);
        assertThat(dltRecord.getLastError()).contains(errMessage);
    }

    @Test
    @DisplayName("FAILED: 일반 예외 발생 시 T1은 롤백, T2는 커밋되어 FAILED 상태가 됨")
    void shouldMarkFailedOnGenericException() throws JsonProcessingException {
        // given
        String errMessage = "unexpected error";
        TestPayload payload = new TestPayload("Fail");
        InboxEventRecord record = createAndSaveTestRecord(eventType, objectMapper.writeValueAsString(payload));
        Long eventId = record.getEventId();
        doThrow(new RuntimeException(errMessage)).when(testEventHandler).handle(any(TestPayload.class));

        // when
        assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(errMessage);

        // then
        InboxEventRecord failedRecord = recordRepository.findByEventId(eventId).orElseThrow();
        assertThat(failedRecord.getInboxEventRecordStatus()).isEqualTo(InboxEventRecordStatus.FAILED);
        assertThat(failedRecord.getLastError()).contains(errMessage);
    }

    @Test
    @DisplayName("DLT: JSON 파싱 실패 시 T1 롤백, T2 커밋되어 DEAD_LETTER 상태가 됨")
    void shouldMarkDeadLetterOnDeserializationError() {
        // given
        String wrongJson = "[1, 2, 3]";
        InboxEventRecord record = createAndSaveTestRecord(eventType, wrongJson);
        Long eventId = record.getEventId();

        // when
        assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("JSON parsing failed, rolling back")
                .hasCauseInstanceOf(JsonProcessingException.class);

        // then
        InboxEventRecord dltRecord = recordRepository.findByEventId(eventId).orElseThrow();
        assertThat(dltRecord.getInboxEventRecordStatus()).isEqualTo(InboxEventRecordStatus.DEAD_LETTER);
        assertThat(dltRecord.getLastError()).contains("DESERIALIZATION_ERROR:");
        verify(testEventHandler, never()).handle(any());
    }

    @Test
    @DisplayName("DLT: 이벤트 핸들러가 없으면 DLT로 마킹됨")
    void shouldMarkDeadLetterWhenNoHandlerFound() throws JsonProcessingException {
        // given
        String unknownEventType = "unknown event";
        TestPayload payload = new TestPayload("no handler");
        InboxEventRecord record = createAndSaveTestRecord(unknownEventType, objectMapper.writeValueAsString(payload));
        Long eventId = record.getEventId();

        // when
        dispatcher.processEvent(eventId, leaseId);

        // then
        InboxEventRecord dltRecord = recordRepository.findByEventId(eventId).orElseThrow();
        assertThat(dltRecord.getInboxEventRecordStatus()).isEqualTo(InboxEventRecordStatus.DEAD_LETTER);
        assertThat(dltRecord.getLastError()).contains("NO_HANDLER:");

    }

    @Test
    @DisplayName("No Action: 레코드를 찾을 수 없으면 아무 동작도 하지 않음")
    void shouldDoNothingWhenRecordNotFound() {
        // given
        long wrongEventId = snowflake.nextId();

        // when
        dispatcher.processEvent(wrongEventId, leaseId);

        // then
        assertThat(recordRepository.findByEventId(wrongEventId)).isEmpty();
        verify(testEventHandler, never()).handle(any(TestPayload.class));
    }

    @Test
    @DisplayName("롤백: 낙관적 락 예외 발생 시 T1 롤백, 상태마킹 없이 그냥 return이라서 상태가 PROCESSING으로 유지됨")
    void shouldNotMarkAnythingOnOptimisticLockingFailure() throws JsonProcessingException {
        // given
        String errMessage = "olf error";
        String payloadJson = objectMapper.writeValueAsString(new TestPayload("olf"));
        InboxEventRecord record = createAndSaveTestRecord(eventType, payloadJson);
        Long eventId = record.getEventId();
        doThrow(new OptimisticLockingFailureException(errMessage)).when(testEventHandler).handle(any(TestPayload.class));
        
        // then
        assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessage(errMessage);
        
        // when
        InboxEventRecord processingRecord = recordRepository.findByEventId(eventId).orElseThrow();
        assertThat(processingRecord.getInboxEventRecordStatus()).isEqualTo(InboxEventRecordStatus.PROCESSING);
        assertThat(processingRecord.getLeaseId()).isEqualTo(leaseId);
    }

    @Test
    @DisplayName("DLT: Payload가 JSON 'null'일 경우 DLT 마킹 (dispatch null check)")
    void shouldMarkDeadLetterWhenPayloadIsJsonNull() {
        // given
        String payloadJson = "null";
        InboxEventRecord record = createAndSaveTestRecord(eventType, payloadJson);
        long eventId = record.getEventId();

        // when
        assertThatThrownBy(() -> dispatcher.processEvent(eventId, leaseId))
                .isInstanceOf(BusinessNotRetryableException.class)
                .hasMessageContaining("Payload is null: expected=");

        // then
        InboxEventRecord dltRecord = recordRepository.findByEventId(eventId).orElseThrow();
        assertThat(dltRecord.getInboxEventRecordStatus()).isEqualTo(InboxEventRecordStatus.DEAD_LETTER);
        assertThat(dltRecord.getLastError()).contains("Payload is null: expected=");
        verify(testEventHandler, never()).handle(any(TestPayload.class));

    }
    private InboxEventRecord createAndSaveTestRecord(String eventType, String payloadJson) {
        ConsumerRecordMetadata metadata = ConsumerRecordMetadata.builder()
                .topic("test-topic")
                .partition(0)
                .offset(0L)
                .build();

        // 이미 선점된 상태의 레코드
        InboxEventRecord record = InboxEventRecord.builder()
                .id(snowflake.nextId())
                .eventId(snowflake.nextId())
                .consumerRecordMetadata(metadata)
                .source(InboxSource.BOOK_CATALOG) // 임의의 값
                .aggregateId(100L)
                .eventType(eventType)
                .payload(payloadJson)
                .inboxEventRecordStatus(InboxEventRecordStatus.PROCESSING) // 초기 상태
                .leaseId(leaseId)
                .leaseUntil(LocalDateTime.now().plusMinutes(5))
                .workerId("test-worker")
                .createdAt(LocalDateTime.now())
                .build();

        return recordRepository.saveAndFlush(record);
    }
}
