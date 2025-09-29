package msa.bookcatalog.infra.aladin.config;

import feign.Logger;
import feign.codec.Decoder;
import msa.bookcatalog.infra.aladin.exception.CustomAladinDecoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AladinFeignConfig {

    @Bean
    public Decoder feignDecoder(ObjectFactory<HttpMessageConverters> messageConverters) {
        return new CustomAladinDecoder(new SpringDecoder(messageConverters));
    }

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}
