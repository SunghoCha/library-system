package msa.bookcatalog.infra.outbox;

import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ContextConfiguration(classes = BookCatalogExternalEventPublisherTest.TestConfig.class)
class BookCatalogExternalEventPublisherTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OutboxEventSender outboxEventSender;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("트랜잭션 커밋 후, 이벤트가 정상적으로 발행 요청되는지 검증한다")
    void afterCommit_publishEvent_async() {
        // given
        BookCatalogChangedEvent event = BookCatalogChangedEvent.builder()
                .eventId(123L)
                .build();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // when:  TransactionTemplate으로 이벤트 발행 로직을 감싸서 실행
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(event);
            return null; // execute 블록은 반환값이 필요함
        });
        // 트랜잭션 커밋되고 AFTER_COMMIT 리스너가 호출

        // then
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    verify(outboxEventSender).send(event);
                });
    }

    @Test
    @DisplayName("이벤트 발행 중 예외가 발생해도, Publisher가 중단되지 않고 정상 처리하는지 검증한다")
    void when_sendFails_publishEvent_logsError() {
        // given
        BookCatalogChangedEvent event = BookCatalogChangedEvent.builder()
                .eventId(456L)
                .build();
        RuntimeException simulatedException = new RuntimeException("Kafka is down");

        // Mock 객체 설정 outboxEventSender.send()가 호출되면 예외를 던지도록 설정
        doThrow(simulatedException).when(outboxEventSender).send(event);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // when 트랜잭셔널 이벤트 발행
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(event);
            return null;
        });

        // then send 메서드가 호출되고, catch 블록이 예외를 잘 처리하는지 확인
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    // 예외가 발생했더라도 send() 메서드는 분명히 호출되었어야 함
                    verify(outboxEventSender).send(event);
                });

    }


    @TestConfiguration
    static class TestConfig {
        // OutboxEventSender 타입의 Mock 객체를 빈으로 등록
        @Primary // 실제 빈 대신 이걸 잡도록 설정
        @Bean
        public OutboxEventSender mockOutboxEventSender() {
            return Mockito.mock(OutboxEventSender.class);
        }
    }
}