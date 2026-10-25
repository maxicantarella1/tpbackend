package com.turismo.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Security Filter del Gateway (OIDC + JWT emitidos por Keycloak).
 * - Autentica: valida firma, emisor y expiración del JWT.
 * - Autoriza por ruta y método según los roles del realm (ADMIN, USUARIO).
 * Regla final: lo que no está listado explícitamente se deniega.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable) // API sin sesión ni cookies: autenticación por Bearer token
            .authorizeExchange(ex -> ex
                .pathMatchers("/actuator/health").permitAll()
                // documentación OpenAPI / Swagger UI (solo describe la API; no ejecuta nada sin token)
                .pathMatchers("/docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/v3/api-docs/**").permitAll()
                // endpoints INTERNOS (Reservas -> Actividades): nunca expuestos a clientes externos
                .pathMatchers("/actividades/*/salidas/*/cupo/**").denyAll()
                // catálogo público: cualquiera puede consultar actividades y salidas
                .pathMatchers(HttpMethod.GET, "/actividades/**").permitAll()
                // administración del catálogo: solo ADMIN
                .pathMatchers("/actividades/**", "/proveedores/**").hasRole("ADMIN")
                // reservas
                .pathMatchers("/reservas/*/reevaluar").hasRole("ADMIN")
                .pathMatchers("/reservas", "/reservas/**").hasAnyRole("USUARIO", "ADMIN")
                .anyExchange().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(convertidor())));
        return http.build();
    }

    /** Keycloak publica los roles de realm en el claim realm_access.roles; Spring espera authorities ROLE_*. */
    private ReactiveJwtAuthenticationConverterAdapter convertidor() {
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        Converter<Jwt, Collection<GrantedAuthority>> roles = SecurityConfig::rolesDeRealm;
        c.setJwtGrantedAuthoritiesConverter(roles);
        return new ReactiveJwtAuthenticationConverterAdapter(c);
    }

    static Collection<GrantedAuthority> rolesDeRealm(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
    }
}
