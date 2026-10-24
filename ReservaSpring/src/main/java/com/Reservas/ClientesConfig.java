package com.Reservas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientesConfig {

    private static SimpleClientHttpRequestFactory factory(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(connectMs);
        f.setReadTimeout(readMs);
        return f;
    }

    @Bean
    RestClient actividadesClient(@Value("${actividades.url}") String url) {
        return RestClient.builder().baseUrl(url).requestFactory(factory(2000, 3000)).build();
    }

    @Bean
    RestClient climaRestClient(@Value("${clima.url}") String url) {
        return RestClient.builder().baseUrl(url).requestFactory(factory(2000, 3000)).build();
    }
}
