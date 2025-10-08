package msa.common.config;

import msa.common.snowflake.DefaultInstanceIdentity;
import msa.common.snowflake.InstanceIdentity;
import msa.common.snowflake.Snowflake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
//@ConditionalOnProperty(name = "snowflake.enabled", havingValue = "true", matchIfMissing = true)
public class SnowflakeConfig {

    @Bean
    public InstanceIdentity instanceIdentity(
            @Value("${snowflake.node-id}") long nodeId) {
        return new DefaultInstanceIdentity(nodeId);
    }

    @Bean
    public Snowflake snowflake(InstanceIdentity identity,
                               @Value("${snowflake.epoch-ms:1704067200000}") long epoch) {
        return new Snowflake(identity.nodeId(), epoch);
    }
}
