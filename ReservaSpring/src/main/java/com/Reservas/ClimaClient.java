package com.Reservas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Cliente de la API pública Open-Meteo (sin API key).
 * Devuelve Optional.empty() si no hay pronóstico (fecha > 16 días, API caída, timeout o respuesta inesperada):
 * el servicio decide qué hacer en ese caso (reserva PENDIENTE), nunca "inventa" un clima.
 */
@Component
public class ClimaClient {

    private static final Logger log = LoggerFactory.getLogger(ClimaClient.class);

    private final RestClient clima;
    private final CircuitBreaker circuito;

    public ClimaClient(@Qualifier("climaRestClient") RestClient clima, CircuitBreakerRegistry registry) {
        this.clima = clima;
        this.circuito = registry.circuitBreaker("clima");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(Daily daily) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Daily(List<Integer> precipitation_probability_max, List<Double> wind_speed_10m_max) { }

    public Optional<Pronostico> pronostico(double lat, double lon, LocalDate fecha) {
        try {
            return circuito.executeSupplier(() -> consultar(lat, lon, fecha));
        } catch (CallNotPermittedException e) {
            log.warn("Circuito 'clima' ABIERTO: se omite la consulta para {}", fecha);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Sin pronóstico para {} ({},{}): {}", fecha, lat, lon, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lanza excepción ante caída, timeout, 5xx o cuerpo inesperado: eso lo cuenta el circuit breaker como fallo.
     * Un 4xx (p. ej. fecha más allá del pronóstico disponible) NO es una caída: devuelve vacío sin contar como fallo.
     */
    private Optional<Pronostico> consultar(double lat, double lon, LocalDate fecha) {
        Respuesta r;
        try {
            r = clima.get()
                    .uri(u -> u.path("/v1/forecast")
                            .queryParam("latitude", lat)
                            .queryParam("longitude", lon)
                            .queryParam("daily", "precipitation_probability_max,wind_speed_10m_max")
                            .queryParam("start_date", fecha)
                            .queryParam("end_date", fecha)
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .body(Respuesta.class);
        } catch (HttpClientErrorException e) {
            return Optional.empty();
        }
        if (r == null || r.daily() == null
                || r.daily().precipitation_probability_max() == null || r.daily().precipitation_probability_max().isEmpty()
                || r.daily().wind_speed_10m_max() == null || r.daily().wind_speed_10m_max().isEmpty()
                || r.daily().precipitation_probability_max().get(0) == null
                || r.daily().wind_speed_10m_max().get(0) == null) {
            throw new IllegalStateException("Respuesta inesperada de la API de clima");
        }
        return Optional.of(new Pronostico(r.daily().precipitation_probability_max().get(0),
                r.daily().wind_speed_10m_max().get(0)));
    }
}
