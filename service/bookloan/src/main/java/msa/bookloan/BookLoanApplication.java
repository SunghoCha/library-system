package msa.bookloan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@EnableKafka
@SpringBootApplication
@EntityScan(basePackages = {"msa.bookloan", "msa.common"})
@ComponentScan(basePackages = {"msa.common", "msa.bookloan"})
public class BookLoanApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookLoanApplication.class, args);
    }
}
