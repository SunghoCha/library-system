package msa.common.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        SnowflakeConfig.class,
        CommonAuditingAutoConfig.class,
})
public class CommonModuleConfig {
}
