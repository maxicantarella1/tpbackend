package com.Reservas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientesConfig {

    @Bean
    RestClient actividadesClient(@Value("${actividades.url}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);  //  ms
        factory.setReadTimeout(3000);
        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(factory)
                .build();
    }
}
