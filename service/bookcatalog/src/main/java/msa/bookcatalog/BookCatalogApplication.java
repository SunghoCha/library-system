package msa.bookcatalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(
        basePackages = {"msa.common", "msa.bookcatalog"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "msa\\.bookcatalog\\.infra\\.aladin\\..*|msa\\.bookcatalog\\.infra\\.batch\\.aladin\\..*"
        )
)
@Import(msa.bookcatalog.infra.aladin.config.AladinModuleConfig.class)
public class BookCatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookCatalogApplication.class, args);
    }

}
