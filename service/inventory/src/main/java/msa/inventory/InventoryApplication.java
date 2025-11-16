package msa.inventory;

import msa.common.config.CommonModuleConfig;
import msa.inventory.infra.config.InfraModulesConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

@ConfigurationPropertiesScan
@SpringBootApplication
@Import({
        InfraModulesConfig.class,
        CommonModuleConfig.class
})
@EntityScan(basePackages = {"msa.inventory", "msa.common"})
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }

}
