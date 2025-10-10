package msa.bookcatalog.infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        AladinModuleConfig.class,
        QueryDslConfig.class
        // 필요 모듈 계속 추가
})
public class InfraModulesConfig {
}
