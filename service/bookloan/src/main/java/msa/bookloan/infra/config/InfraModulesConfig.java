package msa.bookloan.infra.config;


import msa.bookloan.infra.config.kafka.KafkaModuleConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        KafkaModuleConfig.class,
        QueryDslConfig.class
        // 필요 모듈 계속 추가
})
public class InfraModulesConfig {
}
