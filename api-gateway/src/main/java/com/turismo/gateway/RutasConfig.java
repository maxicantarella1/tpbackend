package com.turismo.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mapa de rutas: el cliente conoce SOLO el Gateway. No se reescribe el path: /actividades y /reservas
 * son la API pública; los puertos y hosts de los servicios quedan ocultos.
 */
@Configuration
public class RutasConfig {

    @Bean
    public RouteLocator rutas(RouteLocatorBuilder builder,
                              @Value("${gateway.url-actividades}") String urlActividades,
                              @Value("${gateway.url-reservas}") String urlReservas) {
        return builder.routes()
                .route("actividades", p -> p.path("/actividades/**", "/proveedores/**").uri(urlActividades))
                // OpenAPI de cada servicio, publicado por el Gateway (los servicios no son alcanzables desde afuera)
                .route("docs-actividades", p -> p.path("/docs/actividades").filters(f -> f.setPath("/v3/api-docs")).uri(urlActividades))
                .route("docs-reservas", p -> p.path("/docs/reservas").filters(f -> f.setPath("/v3/api-docs")).uri(urlReservas))
                .route("reservas", p -> p.path("/reservas/**").uri(urlReservas))
                .build();
    }
}
