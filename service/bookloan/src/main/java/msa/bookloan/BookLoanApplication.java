package msa.bookloan;

import msa.bookloan.infra.config.InfraModulesConfig;
import msa.common.config.CommonModuleConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
@ConfigurationPropertiesScan
@Import({
        InfraModulesConfig.class,
        CommonModuleConfig.class
})
@EntityScan(basePackages = {"msa.bookloan", "msa.common"})
public class BookLoanApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookLoanApplication.class, args);
    }
}
