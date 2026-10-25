package com.Reservas;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    /** El "server" documentado es el Gateway (única entrada pública), no el puerto interno del servicio. */
    @Bean
    OpenAPI openAPI(@Value("${openapi.server-url:http://localhost:8080}") String serverUrl) {
        return new OpenAPI()
                .info(new Info().title("Servicio de Reservas").version("1.0")
                        .description("Reservas de actividades: temporada, participantes, clima (API externa), modificar, cancelar y reevaluar. "
                                + "Un USUARIO solo accede a sus reservas."))
                .servers(List.of(new Server().url(serverUrl)))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
