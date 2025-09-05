package msa.bookcatalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import msa.common.snowflake.Snowflake;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UtilConfig {

    @Bean
    public Snowflake snowflake() {
        return new Snowflake();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
