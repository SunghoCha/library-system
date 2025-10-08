package msa.bookloan.infra.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI base() {
        return new OpenAPI()
                .info(new Info()
                        .title("Library API")
                        .version("0.1.0")
                        .description("Library microservices"));
    }
}
