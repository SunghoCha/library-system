package msa.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class JpaConfig {

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
