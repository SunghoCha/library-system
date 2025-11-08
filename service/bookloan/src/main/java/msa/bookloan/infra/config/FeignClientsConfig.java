package msa.bookloan.infra.config;

import msa.bookloan.adapter.out.MemberHttpAdapter;
import msa.bookloan.adapter.out.client.MemberServiceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.clients.enabled",
        havingValue = "true",
        matchIfMissing = false
)
@EnableFeignClients(clients = msa.bookloan.adapter.out.client.MemberServiceClient.class)
public class FeignClientsConfig {

    @Bean
    public MemberHttpAdapter memberHttpAdapter(MemberServiceClient client) {
        return new MemberHttpAdapter(client);
    }
}
