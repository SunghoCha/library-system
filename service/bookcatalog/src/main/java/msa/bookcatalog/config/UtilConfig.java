package msa.bookcatalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import msa.common.snowflake.Snowflake;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class UtilConfig {

    @Bean
    public Snowflake snowflake() {
        return new Snowflake();
    }

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.build();
    }
}
