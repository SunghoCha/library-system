package msa.bookcatalog.infra.outbox.recorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecord;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecordRepository;
import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;
import msa.common.events.EventType;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventRecorderTest {

    private EventRecorder eventRecorder;

    @Mock
    private Snowflake snowflake;

    @Mock
    private BookCatalogOutboxEventRecordRepository eventRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final String testTopic = "book-catalog-changed-topic";

    @BeforeEach
    void setUp() {
        // 생성자를 통해 직접 topic 값을 전달
        eventRecorder = new EventRecorder(snowflake, objectMapper, eventRecordRepository, "book-catalog-changed-topic");
    }

    @Test
    @DisplayName("save: BookCatalogChangedEvent를 받아 OutboxEventRecord를 정상적으로 저장한다")
    void save_success() throws Exception {
        // given
        long eventId = 1L;
        long bookId = 12345L;

        BookCatalogChangedEvent event = BookCatalogChangedEvent.builder()
                .eventId(eventId)
                .eventType(EventType.CREATED)
                .bookId(bookId)
                .aggregateVersion(1L)
                .title("New Title")
                .author("New Author")
                .category(new CategoryRef(1, "카테고리이름"))
                .bookType(new BookTypeRef("NEW_RELEASE", "신간"))
                .occurredAt(LocalDateTime.now())
                .build();

        long expectedDbId = 9999L;
        String expectedPayload = objectMapper.writeValueAsString(BookCatalogChangedExternalEventPayload.of(event));

        given(snowflake.nextId()).willReturn(expectedDbId);

        // ArgumentCaptor: 메소드에 전달된 인자를 캡처하여 검증할 때 사용
        ArgumentCaptor<BookCatalogOutboxEventRecord> captor = ArgumentCaptor.forClass(BookCatalogOutboxEventRecord.class);

        // when
        eventRecorder.save(event);

        // then
        // 1. repository의 save 메소드가 1번 호출되었는지 검증
        verify(eventRecordRepository).save(captor.capture());

        // 2. save 메소드에 전달된 객체의 필드 값들을 검증
        BookCatalogOutboxEventRecord savedRecord = captor.getValue();
        assertThat(savedRecord.getId()).isEqualTo(expectedDbId);
        assertThat(savedRecord.getEventId()).isEqualTo(event.getEventId());
        assertThat(savedRecord.getAggregateId()).isEqualTo(String.valueOf(bookId));
        assertThat(savedRecord.getEventType()).isEqualTo(event.getEventType());
        assertThat(savedRecord.getPayload()).isEqualTo(expectedPayload);
        assertThat(savedRecord.getOutboxEventRecordStatus()).isEqualTo(OutboxEventRecordStatus.NEW);
        assertThat(savedRecord.getRouting().getTopic()).isEqualTo(testTopic);
        assertThat(savedRecord.getRouting().getPartitionKey()).isEqualTo(String.valueOf(bookId));
    }

    @Test
    @DisplayName("save: 페이로드 직렬화에 실패하면 IllegalStateException을 던진다")
    void save_fail_whenSerializationFails() throws JsonProcessingException {
        // given
        BookCatalogChangedEvent event = BookCatalogChangedEvent.builder()
                .eventId(2L)
                .bookId(1L)
                .title("T")
                .author("A")
                .category(new CategoryRef(1, "카테고리이름"))
                .bookType(new BookTypeRef("NEW_RELEASE", "신간"))
                .eventType(EventType.CREATED)
                .occurredAt(LocalDateTime.now())
                .build();

        // ObjectMapper를 Mock으로 만들고 예외를 던지도록 설정
        ObjectMapper mockObjectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        // 테스트 대상 객체에 Mock ObjectMapper를 주입
        eventRecorder = new EventRecorder(snowflake, mockObjectMapper, eventRecordRepository, testTopic);

        given(mockObjectMapper.writeValueAsString(any())).willThrow(new JsonProcessingException("serialization error"){});

        // when & then
        assertThatThrownBy(() -> eventRecorder.save(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Outbox payload serialize failed");

        // repository의 save가 호출되지 않았는지 검증
        verify(eventRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("markAsPublished: 이벤트 상태를 PUBLISHED로 정상적으로 변경한다")
    void markAsPublished_success() {
        // given
        Long eventId = 1L;

        // when
        eventRecorder.markAsPublished(eventId);

        // then
        verify(eventRecordRepository).updateStatusIfCurrent(
                eventId,
                OutboxEventRecordStatus.PUBLISHING,
                OutboxEventRecordStatus.PUBLISHED
        );
    }

    @Test
    @DisplayName("markAsDeadLetter: 이벤트 상태를 DEAD_LETTER로 정상적으로 변경한다")
    void markAsDeadLetter_success() {
        // given
        Long eventId = 1L;
        String errorMessage = "Failed to publish after retries";
        List<OutboxEventRecordStatus> expectedFromStatuses =
                List.of(OutboxEventRecordStatus.FAILED, OutboxEventRecordStatus.PUBLISHING);

        // when
        eventRecorder.markAsDeadLetter(eventId, errorMessage);

        // then
        verify(eventRecordRepository).toDeadLetterIfCurrent(
                eventId,
                expectedFromStatuses,
                OutboxEventRecordStatus.DEAD_LETTER,
                errorMessage
        );
    }

    @Test
    @DisplayName("handleFailure: 이벤트 상태를 FAILED로 정상적으로 변경한다")
    void handleFailure_success() {
        // given
        Long eventId = 1L;
        String errorMessage = "Temporary failure";

        // when
        eventRecorder.handleFailure(eventId, errorMessage);

        // then
        verify(eventRecordRepository).failAndIncrementIfCurrent(
                eventId,
                OutboxEventRecordStatus.PUBLISHING,
                OutboxEventRecordStatus.FAILED,
                errorMessage
        );
    }
}