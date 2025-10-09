package msa.bookcatalog.infra.config;

import msa.bookcatalog.adapter.out.client.aladin.AladinClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name="app.aladin.enabled", havingValue="true", matchIfMissing=true)
@EnableFeignClients(basePackageClasses = AladinClient.class)
@Import({
        AladinFeignConfig.class,
})
public class AladinModuleConfig {}
