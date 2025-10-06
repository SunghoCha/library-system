package msa.bookloan;

import msa.bookloan.config.InfraModulesConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
@EntityScan(basePackages = {"msa.bookloan", "msa.common"})
@Import(InfraModulesConfig.class)
public class BookLoanApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookLoanApplication.class, args);
    }
}
