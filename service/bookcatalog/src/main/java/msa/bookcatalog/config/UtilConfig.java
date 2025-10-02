package msa.bookcatalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import msa.common.snowflake.Snowflake;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
    //@ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper(ObjectProvider<Jackson2ObjectMapperBuilder> provider) {
        Jackson2ObjectMapperBuilder b = provider.getIfAvailable(Jackson2ObjectMapperBuilder::new);
        return b.build();
    }
}
