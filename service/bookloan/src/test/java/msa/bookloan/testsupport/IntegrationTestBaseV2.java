package msa.bookloan.testsupport;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@ExtendWith(DatabaseClearExtension.class)
@Testcontainers
public abstract class IntegrationTestBaseV2 {

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

//    @Container
//    private static final MySQLContainer<?> MYSQL =
//            new MySQLContainer<>(DockerImageName.parse("mysql:8.3.0"))
//                    .withDatabaseName("testdb")
//                    .withUsername("test")
//                    .withPassword("test");


    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // 컨슈머/프로듀서용
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // Admin(토픽 생성)도 동일 브로커 사용
        r.add("spring.kafka.admin.properties.bootstrap.servers", KAFKA::getBootstrapServers);

        // 테스트 편의 설정
        r.add("spring.kafka.listener.missing-topics-fatal", () -> "false");
        r.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");

        // 프로젝트 플래그
        r.add("app.kafka.enabled", () -> "true");

        // MYSQL 설정
//        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
//        r.add("spring.datasource.username", MYSQL::getUsername);
//        r.add("spring.datasource.password", MYSQL::getPassword);
    }

    @TestConfiguration
    public static class KafkaTopics {

        private static NewTopic topic(String name) {
            return TopicBuilder.name(name).partitions(1).replicas(1).build();
        }

        @Bean
        NewTopic topicMemberCheck(@Value("${app.kafka.topic-member-check}") String name) {
            return topic(name);
        }

        @Bean
        NewTopic topicInventoryReserve(@Value("${app.kafka.topic-inventory-reserve}") String name) {
            return topic(name);
        }

        @Bean
        NewTopic topicPointCharge(@Value("${app.kafka.topic-point-charge}") String name) {
            return topic(name);
        }

        @Bean
        NewTopic topicPointRefund(@Value("${app.kafka.topic-point-refund}") String name) {
            return topic(name);
        }

        @Bean
        NewTopic topicShippingSchedule(@Value("${app.kafka.topic-shipping-schedule}") String name) {
            return topic(name);
        }

        @Bean
        NewTopic topicInventoryRelease(@Value("${app.kafka.topic-inventory-release}") String name) {
            return topic(name);
        }

        @Bean
        NewTopic topicSagaReplies(@Value("${app.kafka.topic-saga-replies}") String name) {
            return topic(name);
        }

        @Bean
        NewTopic topicCatalogChanged(@Value("${app.kafka.topic-catalog-changed}") String name) {
            return topic(name);
        }

    }


}
