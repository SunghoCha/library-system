package msa.bookloan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
@ComponentScan(basePackages = {"msa.common.events", "msa.bookloan"})
public class BookLoanApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookLoanApplication.class, args);
    }
}
