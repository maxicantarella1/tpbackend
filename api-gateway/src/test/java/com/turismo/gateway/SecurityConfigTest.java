package com.turismo.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Solo prueba decisiones del Security Filter que se toman ANTES de rutear (401/403), por eso no hace falta
 * levantar los microservicios ni Keycloak.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri="
})
@AutoConfigureWebTestClient
class SecurityConfigTest {

    @Autowired
    WebTestClient web;

    @Test
    void sinTokenEnReservasEs401() {
        web.get().uri("/reservas").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void usuarioNoPuedeAdministrarElCatalogo() {
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_USUARIO")))
                .post().uri("/actividades").exchange().expectStatus().isForbidden();
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_USUARIO")))
                .get().uri("/proveedores").exchange().expectStatus().isForbidden();
    }

    @Test
    void sinTokenNoSePuedeEscribirEnElCatalogo() {
        web.post().uri("/actividades").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void usuarioNoPuedeReevaluar() {
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_USUARIO")))
                .post().uri("/reservas/1/reevaluar").exchange().expectStatus().isForbidden();
    }

    @Test
    void endpointsInternosDeCupoNoSeExponenNiAlAdmin() {
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .patch().uri("/actividades/1/salidas/2026-12-01/cupo").exchange().expectStatus().isForbidden();
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .post().uri("/actividades/1/salidas/2026-12-01/cupo/devolucion").exchange().expectStatus().isForbidden();
    }

    @Test
    void rutasDesconocidasSeDenieganYNoSeRevelan() {
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .get().uri("/otra-cosa").exchange().expectStatus().isForbidden();
    }

    @Test
    void laDocumentacionEsPublica() {
        // sin token no debe ser 401/403 (el destino no existe en el test: puede dar 5xx, pero no rechazo de seguridad)
        web.get().uri("/swagger-ui.html").exchange().expectStatus().value(st ->
                org.junit.jupiter.api.Assertions.assertFalse(st == 401 || st == 403, "status " + st));
    }
}
