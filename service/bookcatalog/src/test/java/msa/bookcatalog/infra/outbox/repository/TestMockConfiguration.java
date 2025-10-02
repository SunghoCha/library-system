//package msa.bookcatalog.infra.outbox.repository;
//
//import msa.bookcatalog.infra.outbox.OutboxEventSender;
//import msa.bookcatalog.infra.outbox.scheduler.BookCatalogOutboxRelayScheduler;
//import msa.bookcatalog.infra.outbox.service.OutboxClaimerService;
//import org.mockito.Mockito;
//import org.springframework.boot.test.context.TestConfiguration;
//import org.springframework.context.annotation.Bean;
//
////테스트에 불필요한 의존성들을 Mock Bean으로 등록
//@TestConfiguration
//public class TestMockConfiguration {
//    @Bean
//    public OutboxEventSender outboxEventSender() {
//        return Mockito.mock(OutboxEventSender.class);
//    }
//
//    @Bean
//    public BookCatalogOutboxRelayScheduler bookCatalogOutboxRelayScheduler() {
//        return Mockito.mock(BookCatalogOutboxRelayScheduler.class);
//    }
//
//    @Bean
//    public OutboxClaimerService outboxClaimerService() {
//        return Mockito.mock(OutboxClaimerService.class);
//    }
//}
