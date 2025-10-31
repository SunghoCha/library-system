package msa.bookloan.adapter.out.persistence.outbox;

import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookloan.testsupport.time.TestClocks;
import msa.common.config.properties.OutboxSchedulerProps;
import msa.common.snowflake.InstanceIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxClaimerServiceTest {

    private OutboxClaimerService outboxClaimerService;

    @Mock
    private InstanceIdentity instanceIdentity;

    @Mock
    private OutboxSchedulerProps properties;

    @Mock
    private OutboxEventRecordRepository outboxRepository;

    private static final String TEST_WORKER_ID = "test-worker-01";
    private static final String TEST_LEASE_ID = UUID.randomUUID().toString();
    private static final int BATCH_SIZE = 10;
    private static final int MAX_RETRY_COUNT = 5;
    private static final Duration LEASE        = ofSeconds(60);

    @BeforeEach
    void setUp() {
        outboxClaimerService = new OutboxClaimerService(
                TestClocks.FIXED_CLOCK, instanceIdentity, properties, outboxRepository);

        // Mockito의 lenient()를 사용하여 불필요한 Stubbing 예외를 방지
        // 모든 테스트에서 사용하지 않을 수 있는 Mock 객체의 기본 동작을 설정
        // (테스트마다 따로 해주기 귀찮아서 이렇게했는데 이게 맞는지는 잘 모르겠음)
        lenient().when(properties.batchSize()).thenReturn(BATCH_SIZE);
        lenient().when(properties.maxRetryCount()).thenReturn(MAX_RETRY_COUNT);
        lenient().when(properties.lease()).thenReturn(LEASE);
        lenient().when(instanceIdentity.workerId()).thenReturn(TEST_WORKER_ID);
    }

    @Test
    @DisplayName("성공: 클레임할 이벤트를 찾아 잠그고, 발행 중으로 표시한 뒤, 이벤트 목록을 반환한다.")
    void claimEvents_Success() {
        // given
        List<Long> eventIds = List.of(1L, 2L, 3L);
        List<OutboxEventRecord> expectedEvents = List.of(
                OutboxEventRecord.builder().id(1L).build(),
                OutboxEventRecord.builder().id(2L).build(),
                OutboxEventRecord.builder().id(3L).build()
        );

        // 1. lockClaimableIds가 ID 목록을 반환
        when(outboxRepository.lockClaimableIds(
                eq(BATCH_SIZE),
                eq(MAX_RETRY_COUNT),
                any(LocalDateTime.class)
        )).thenReturn(eventIds);

        // 2. markPublishing이 성공적으로 3건을 업데이트
        when(outboxRepository.markPublishing(
                eq(eventIds),
                anyString(),
                eq(TEST_WORKER_ID),
                any(LocalDateTime.class)
        )).thenReturn((long) eventIds.size());

        // 3. findPublishingByIdsOrderByOccurredAt이 이벤트 엔티티 목록을 반환
        when(outboxRepository.findPublishingByIdsOrderByOccurredAt(eventIds))
                .thenReturn(expectedEvents);

        // when
        List<OutboxEventRecord> actualEvents = outboxClaimerService.claimEvents();

        // then
        assertThat(actualEvents).isEqualTo(expectedEvents);

        // 캡처로 pickedAt/leaseUntil 검증
        ArgumentCaptor<LocalDateTime> leaseUntilCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> leaseIdCap = ArgumentCaptor.forClass(String.class);

        verify(outboxRepository).markPublishing(
                eq(eventIds), leaseIdCap.capture(), eq(TEST_WORKER_ID), leaseUntilCap.capture()
        );

        LocalDateTime expectedLeaseUntil = LocalDateTime.now(TestClocks.FIXED_CLOCK).plus(LEASE);
        assertThat(expectedLeaseUntil).isEqualTo(leaseUntilCap.getValue());

        // verify (메서드 호출 순서 및 파라미터 검증)
        verify(outboxRepository).lockClaimableIds(
                eq(BATCH_SIZE),
                eq(MAX_RETRY_COUNT),
                any(LocalDateTime.class)
        );

        verify(outboxRepository).findPublishingByIdsOrderByOccurredAt(eventIds);
    }

    @Test
    @DisplayName("클레임할 이벤트 없음: lockClaimableIds가 빈 리스트를 반환하면 빈 리스트를 반환한다.")
    void claimEvents_WhenNoClaimableEvents() {
        // given
        // 1. lockClaimableIds가 빈 리스트를 반환
        when(outboxRepository.lockClaimableIds(
                eq(BATCH_SIZE),
                eq(MAX_RETRY_COUNT),
                any(LocalDateTime.class)
        )).thenReturn(List.of());

        // when
        List<OutboxEventRecord> actualEvents = outboxClaimerService.claimEvents();

        // then
        assertThat(actualEvents).isEmpty();

        // verify
        // markPublishing이나 find... 메서드가 호출되지 않았는지 검증
        verify(outboxRepository, never()).markPublishing(anyList(), anyString(), anyString(), any(LocalDateTime.class));
        verify(outboxRepository, never()).findPublishingByIdsOrderByOccurredAt(anyList());
        // workerId도 호출될 필요 없음
        verify(instanceIdentity, never()).workerId();
    }

    @Test
    @DisplayName("클레임 실패: markPublishing이 0을 반환(경합 실패)하면 빈 리스트를 반환한다.")
    void claimEvents_WhenClaimFails() {
        // given
        List<Long> eventIds = List.of(1L, 2L);

        // 1. lockClaimableIds가 ID 목록을 반환
        when(outboxRepository.lockClaimableIds(
                eq(BATCH_SIZE),
                eq(MAX_RETRY_COUNT),
                any(LocalDateTime.class)
        )).thenReturn(eventIds);

        // 2. markPublishing이 0을 반환 (업데이트 실패)
        when(outboxRepository.markPublishing(
                eq(eventIds),
                anyString(),
                eq(TEST_WORKER_ID),
                any(LocalDateTime.class)
        )).thenReturn(0L);

        // when
        List<OutboxEventRecord> actualEvents = outboxClaimerService.claimEvents();

        // then
        assertThat(actualEvents).isEmpty();

        // verify
        verify(outboxRepository).lockClaimableIds(
                eq(BATCH_SIZE),
                eq(MAX_RETRY_COUNT),
                any(LocalDateTime.class)
        );
        verify(outboxRepository).markPublishing(eq(eventIds), any(), eq(TEST_WORKER_ID), any(LocalDateTime.class));
        verify(instanceIdentity).workerId();

        // find... 메서드가 호출되지 않았는지 검증
        verify(outboxRepository, never()).findPublishingByIdsOrderByOccurredAt(anyList());
    }
}

