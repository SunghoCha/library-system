package msa.bookcatalog.infra.aladin.config;

import msa.bookcatalog.infra.aladin.AladinClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name="app.aladin.enabled", havingValue="true", matchIfMissing=true)
@EnableFeignClients(basePackageClasses = AladinClient.class)
@ComponentScan(basePackages={
        "msa.bookcatalog.infra.aladin",
        "msa.bookcatalog.infra.batch.aladin"
})
public class AladinModuleConfig {}
