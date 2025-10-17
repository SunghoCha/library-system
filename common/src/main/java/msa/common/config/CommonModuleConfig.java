package msa.common.config;


import msa.common.config.properties.OutboxSchedulerProps;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        SnowflakeConfig.class,
        CommonAuditingAutoConfig.class,
})
@EnableConfigurationProperties(OutboxSchedulerProps.class)
public class CommonModuleConfig {
}
