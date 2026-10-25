package com.turismo.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Traduce el JWT ya validado a cabeceras internas para los microservicios y agrega el secreto de la zona de confianza.
 * Primero BORRA cualquier X-User-* / X-Internal-Secret que haya mandado el cliente: nadie puede suplantar identidad.
 */
@Component
public class IdentidadFilter implements GlobalFilter, Ordered {

    static final String HEADER_SECRET = "X-Internal-Secret";
    static final String HEADER_USER = "X-User-Name";
    static final String HEADER_ROLES = "X-User-Roles";

    private final String secreto;

    public IdentidadFilter(@Value("${seguridad.gateway-secret:}") String secreto) {
        this.secreto = secreto;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> conIdentidad(exchange, auth))
                .defaultIfEmpty(conIdentidad(exchange, null)) // anónimo (catálogo público)
                .flatMap(chain::filter);
    }

    private ServerWebExchange conIdentidad(ServerWebExchange exchange, Authentication auth) {
        return exchange.mutate().request(r -> r.headers(h -> {
            h.remove(HEADER_SECRET);
            h.remove(HEADER_USER);
            h.remove(HEADER_ROLES);
            if (!secreto.isBlank()) h.set(HEADER_SECRET, secreto);
            if (auth instanceof JwtAuthenticationToken jwt) {
                String usuario = jwt.getToken().getClaimAsString("preferred_username");
                if (usuario != null) h.set(HEADER_USER, usuario);
                h.set(HEADER_ROLES, jwt.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(a -> a.startsWith("ROLE_"))
                        .map(a -> a.substring(5))
                        .collect(Collectors.joining(",")));
            }
        })).build();
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE - 10; }
}
