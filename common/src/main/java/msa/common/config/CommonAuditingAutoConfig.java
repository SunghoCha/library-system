package msa.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Configuration
@ConditionalOnClass(jakarta.persistence.EntityManager.class)
public class CommonAuditingAutoConfig {

    @Bean @ConditionalOnMissingBean
    public Clock clock() { return Clock.systemUTC(); }

    @Bean @ConditionalOnMissingBean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }



    // TODO : 시큐리티 추가 후 설정 예정
    @Bean
    public AuditorAware<String> auditorProvider() {

        return () -> Optional.of("system");
    }

//    @Bean
//    public AuditorAware<String> auditorProvider() {
//        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
//                .filter(Authentication::isAuthenticated)
//                .map(Authentication::getName)
//                .or(() -> Optional.of("system"));
//    }
}
