package msa.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CommonTimeAutoConfig {
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() { return Clock.systemUTC(); }
}
