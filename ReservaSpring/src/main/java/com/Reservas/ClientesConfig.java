package com.Reservas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.time.Duration;

@Configuration
public class ClientesConfig {

    private static SimpleClientHttpRequestFactory factory(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(connectMs);
        f.setReadTimeout(readMs);
        return f;
    }

    @Bean
    RestClient actividadesClient(@Value("${actividades.url}") String url,
                                 @Value("${seguridad.gateway-secret:}") String secreto) {
        RestClient.Builder b = RestClient.builder().baseUrl(url).requestFactory(factory(2000, 3000));
        if (!secreto.isBlank()) b.defaultHeader(com.turismo.common.Seguridad.HEADER_SECRET, secreto); // zona de confianza
        return b.build();
    }

    @Bean
    RestClient climaRestClient(@Value("${clima.url}") String url) {
        return RestClient.builder().baseUrl(url).requestFactory(factory(2000, 3000)).build();
    }

    /**
     * Circuit breaker: tras demasiados fallos seguidos "abre" y responde rápido sin llamar al servicio caído;
     * pasado el tiempo de espera deja pasar unas pocas llamadas de prueba (half-open) y si andan, vuelve a cerrar.
     */
    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(
            @Value("${resiliencia.ventana:10}") int ventana,
            @Value("${resiliencia.minimo-llamadas:5}") int minimoLlamadas,
            @Value("${resiliencia.umbral-fallos:50}") float umbralFallos,
            @Value("${resiliencia.espera-abierto-seg:30}") long esperaAbiertoSeg) {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(ventana)
                .minimumNumberOfCalls(minimoLlamadas)
                .failureRateThreshold(umbralFallos)
                .waitDurationInOpenState(Duration.ofSeconds(esperaAbiertoSeg))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build());
    }
}
