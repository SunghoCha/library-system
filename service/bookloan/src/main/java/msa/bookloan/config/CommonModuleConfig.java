package msa.bookloan.config;


import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {
        "msa.common",
})
public class CommonModuleConfig {
}
