package com.turismo.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Mecanismo de la zona de confianza: un microservicio solo atiende pedidos que traigan el secreto interno.
 * El Gateway y los demás servicios lo envían; un cliente que llegue directo al puerto del servicio recibe 403.
 */
public class ConfianzaGatewayFilter extends OncePerRequestFilter {

    private final byte[] secreto;

    public ConfianzaGatewayFilter(String secreto) {
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return p.startsWith("/actuator/health") || p.startsWith("/swagger-ui") || p.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String recibido = req.getHeader(Seguridad.HEADER_SECRET);
        // comparación en tiempo constante: no filtra información por tiempos de respuesta
        if (recibido != null && MessageDigest.isEqual(recibido.getBytes(StandardCharsets.UTF_8), secreto)) {
            chain.doFilter(req, res);
            return;
        }
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"mensaje\":\"Acceso directo no permitido: use el API Gateway\"}");
    }
}
