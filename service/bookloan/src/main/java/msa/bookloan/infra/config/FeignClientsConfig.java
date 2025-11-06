package msa.bookloan.infra.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(clients = msa.bookloan.adapter.out.client.MemberServiceClient.class)
public class FeignClientsConfig {
}
