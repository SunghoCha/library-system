package msa.bookcatalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableFeignClients
@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(basePackages = {"msa.common", "msa.bookcatalog"})
public class BookCatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookCatalogApplication.class, args);
    }

}
