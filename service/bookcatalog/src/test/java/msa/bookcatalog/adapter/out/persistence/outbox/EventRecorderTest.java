package msa.bookcatalog.adapter.out.persistence.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookcatalog.application.event.BookCatalogChangedEvent;
import msa.bookcatalog.application.event.CatalogEventType;
import msa.bookcatalog.testsupport.time.TestClocks;
import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static java.time.LocalDateTime.now;
import static java.time.LocalDateTime.ofInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventRecorderTest {

    private EventRecorder eventRecorder;
    private final Clock fixedClock = TestClocks.FIXED_CLOCK;

    @Mock private Snowflake snowflake;
    @Mock private OutboxEventRecordRepository eventRecordRepository;
    @Mock private OutboxRoutingResolver<BookCatalogChangedEvent> routingResolver;

    @Captor
    private ArgumentCaptor<OutboxEventRecord> recordCaptor;

    @Captor private ArgumentCaptor<List<OutboxEventRecord>> recordListCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final String testTopic = "book-catalog-changed-topic";

    @BeforeEach
    void setUp() {
        // 생성자를 통해 의존성과 topic 값을 직접 주입
        eventRecorder = new EventRecorder(fixedClock, snowflake, objectMapper, eventRecordRepository, routingResolver);
    }

    @Test
    @DisplayName("save: BookCatalogChangedEvent를 받아 OutboxEventRecord를 정상적으로 저장한다")
    void save_success() {
        // given
        BookCatalogChangedEvent event = createTestEvent(1L, 12345L);
        long expectedDbId = 9999L;
        when(snowflake.nextId()).thenReturn(expectedDbId);

        OutboxRouting expectedRouting = new OutboxRouting(testTopic, String.valueOf(event.getAggregateId()));
        when(routingResolver.doResolve(any(BookCatalogChangedEvent.class))).thenReturn(expectedRouting);

        when(eventRecordRepository.upsertOutbox(
                eq(expectedDbId),
                eq(event.getEventId()),
                eq(event.getEventType()),
                eq(String.valueOf(event.getAggregateId())),
                eq(event.getAggregateType()),
                eq(event.getAggregateVersion()),
                anyString(), // payload JSON은 직렬화 결과라 엄격 매칭 불필요
                eq(expectedRouting.getTopic()),
                eq(expectedRouting.getPartitionKey()),
                eq(event.getOccurredAt())
        )).thenReturn(1);


        // when
        boolean isNew = eventRecorder.save(event);

        // then
        assertThat(isNew).isTrue();
        verify(eventRecordRepository, times(1)).upsertOutbox(
                eq(expectedDbId),
                eq(event.getEventId()),
                eq(event.getEventType()),
                eq(String.valueOf(event.getAggregateId())),
                eq(event.getAggregateType()),
                eq(event.getAggregateVersion()),
                anyString(),
                eq(expectedRouting.getTopic()),
                eq(expectedRouting.getPartitionKey()),
                eq(event.getOccurredAt())
        );
        verifyNoMoreInteractions(eventRecordRepository);
    }

    @Test
    @DisplayName("saveAll: 여러 개의 이벤트를 받아 정상적으로 저장한다")
    void saveAll_success() {
        // given
        BookCatalogChangedEvent event1 = createTestEvent(1L, 100L);
        BookCatalogChangedEvent event2 = createTestEvent(2L, 200L);
        List<BookCatalogChangedEvent> events = List.of(event1, event2);

        given(snowflake.nextId()).willReturn(1001L, 1002L);

        OutboxRouting routing1 = new OutboxRouting(testTopic, String.valueOf(event1.getAggregateId()));
        OutboxRouting routing2 = new OutboxRouting(testTopic, String.valueOf(event2.getAggregateId()));
        given(routingResolver.doResolve(event1)).willReturn(routing1);
        given(routingResolver.doResolve(event2)).willReturn(routing2);
        // when
        eventRecorder.saveAll(events);

        // then
        verify(eventRecordRepository).saveAll(recordListCaptor.capture());
        List<OutboxEventRecord> savedRecords = recordListCaptor.getValue();

        assertThat(savedRecords).hasSize(2);
        assertThat(savedRecords.get(0).getEventId()).isEqualTo(1L);
        assertThat(savedRecords.get(0).getId()).isEqualTo(1001L);
        assertThat(savedRecords.get(1).getEventId()).isEqualTo(2L);
        assertThat(savedRecords.get(1).getId()).isEqualTo(1002L);
    }

    @Test
    @DisplayName("saveAll: 비어 있거나 null인 리스트에 대해서는 아무 작업도 수행하지 않는다")
    void saveAll_withEmptyOrNullList_doesNothing() {
        // when
        eventRecorder.saveAll(Collections.emptyList());
        eventRecorder.saveAll(null);

        // then
        verify(eventRecordRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("save: 페이로드 직렬화에 실패하면 IllegalStateException을 던진다")
    void save_fail_whenSerializationFails() throws JsonProcessingException {
        // given
        BookCatalogChangedEvent event = createTestEvent(2L, 1L);
        ObjectMapper mockObjectMapper = mock(ObjectMapper.class);
        eventRecorder = new EventRecorder(fixedClock, snowflake, mockObjectMapper, eventRecordRepository, routingResolver);

        given(mockObjectMapper.writeValueAsString(any())).willThrow(new JsonProcessingException("serialization error"){});

        // when & then
        assertThatThrownBy(() -> eventRecorder.save(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Outbox payload serialize failed");

        verify(eventRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("markPublishedByEventId: Repository를 호출하고 결과를 그대로 반환한다")
    void markPublishedByEventId_delegatesAndReturnsResult() {
        // given
        Long eventId = 1L;
        String workerId = "worker-1";
        LocalDateTime claimedAt = now(fixedClock);
        // Repository가 1(성공)을 반환하도록 설정
        when(eventRecordRepository.markPublishedByEventId(eventId, workerId, claimedAt, now(fixedClock))).thenReturn(1L);

        // when
        long result = eventRecorder.markPublishedByEventId(eventId, workerId, claimedAt);

        // then
        // Repository의 해당 메서드가 정확한 인자와 함께 호출되었는지 검증
        verify(eventRecordRepository).markPublishedByEventId(eventId, workerId, claimedAt, now(fixedClock));
        // EventRecorder가 Repository의 결과를 그대로 반환했는지 검증
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("markFailedByEventId: Repository를 호출하고 결과를 그대로 반환한다")
    void markFailedByEventId_delegatesAndReturnsResult() {
        // given
        Long eventId = 2L;
        String workerId = "worker-2";
        LocalDateTime claimedAt = now(fixedClock);
        String reason = "Kafka Error";
        when(eventRecordRepository.markFailedByEventId(eventId, workerId, claimedAt, reason, now(fixedClock))).thenReturn(1L);

        // when
        long result = eventRecorder.markFailedByEventId(eventId, workerId, claimedAt, reason);

        // then
        verify(eventRecordRepository).markFailedByEventId(eventId, workerId, claimedAt, reason, now(fixedClock));
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("markDeadLetter: Repository를 호출하고 결과를 그대로 반환한다")
    void markDeadLetter_delegatesAndReturnsResult() {
        // given
        Long eventId = 3L;
        String error = "Max retries exceeded";
        when(eventRecordRepository.markDeadFromFailed(eventId, error, now(fixedClock))).thenReturn(1L);

        // when
        long result = eventRecorder.markDeadLetter(eventId, error);

        // then
        verify(eventRecordRepository).markDeadFromFailed(eventId, error, now(fixedClock));
        assertThat(result).isEqualTo(1);
    }

    private BookCatalogChangedEvent createTestEvent(Long eventId, Long bookId) {
        return BookCatalogChangedEvent.builder()
                .eventId(eventId)
                .eventType(CatalogEventType.CREATED.getValue())
                .bookId(bookId)
                .aggregateVersion(1L)
                .title("New Title")
                .author("New Author")
                .category(new CategoryRef(1, "카테고리이름"))
                .bookType(new BookTypeRef("NEW_RELEASE", "신간"))
                .occurredAt(ofInstant(fixedClock.instant(), ZoneOffset.UTC))
                .build();
    }
}