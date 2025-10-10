package msa.bookcatalog;

import msa.bookcatalog.infra.config.InfraModulesConfig;
import msa.common.config.CommonModuleConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ConfigurationPropertiesScan
@Import({
        InfraModulesConfig.class,
        CommonModuleConfig.class
})
@EntityScan(basePackages = {"msa.bookcatalog", "msa.common"})
public class BookCatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookCatalogApplication.class, args);
    }

}
