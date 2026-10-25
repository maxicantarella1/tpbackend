package com.Reservas;

import com.turismo.common.Nivel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.test.web.client.ExpectedCount;
import com.turismo.common.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;
import org.springframework.http.HttpMethod;

class ClimaClientTest {

    private MockRestServiceServer server;
    private ClimaClient cliente;

    private void init() {
        RestClient.Builder b = RestClient.builder().baseUrl("https://clima.test");
        server = MockRestServiceServer.bindTo(b).build();
        cliente = new ClimaClient(b.build(), CircuitBreakerRegistry.ofDefaults());
    }

    @Test
    void devuelvePronosticoCuandoLaApiResponde() {
        init();
        server.expect(requestTo(startsWith("https://clima.test/v1/forecast"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"daily\":{\"precipitation_probability_max\":[70],\"wind_speed_10m_max\":[35.5]}}",
                        MediaType.APPLICATION_JSON));
        var p = cliente.pronostico(-31.4, -64.2, LocalDate.of(2026, 11, 1));
        assertTrue(p.isPresent());
        assertEquals(70, p.get().probLluvia());
        assertEquals(35.5, p.get().vientoKmh());
    }

    @Test
    void devuelveVacioSiLaApiFalla() {
        init();
        server.expect(requestTo(startsWith("https://clima.test/v1/forecast")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertTrue(cliente.pronostico(0, 0, LocalDate.of(2026, 11, 1)).isEmpty());
    }

    @Test
    void devuelveVacioSiLaRespuestaEsInesperada() {
        init();
        server.expect(requestTo(startsWith("https://clima.test/v1/forecast")))
                .andRespond(withSuccess("{\"otra\":1}", MediaType.APPLICATION_JSON));
        assertTrue(cliente.pronostico(0, 0, LocalDate.of(2026, 11, 1)).isEmpty());
    }

    private void initConCircuitoSensible() {
        RestClient.Builder b = RestClient.builder().baseUrl("https://clima.test");
        server = MockRestServiceServer.bindTo(b).build();
        var cfg = CircuitBreakerConfig.custom().slidingWindowSize(4).minimumNumberOfCalls(2)
                .failureRateThreshold(50).waitDurationInOpenState(java.time.Duration.ofMinutes(5)).build();
        registro = CircuitBreakerRegistry.of(cfg);
        cliente = new ClimaClient(b.build(), registro);
    }

    private CircuitBreakerRegistry registro;

    @Test
    void conLaApiCaidaElCircuitoAbreYDejaDeLlamar() {
        initConCircuitoSensible();
        // solo se esperan 2 llamadas reales: una tercera haría fallar el test (el circuito debe cortarla)
        server.expect(ExpectedCount.times(2), requestTo(startsWith("https://clima.test/v1/forecast")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        for (int i = 0; i < 4; i++) {
            assertTrue(cliente.pronostico(0, 0, LocalDate.of(2026, 11, 1)).isEmpty());
        }

        assertEquals(CircuitBreaker.State.OPEN, registro.circuitBreaker("clima").getState());
        server.verify();
    }

    @Test
    void unaRespuestaInesperadaTambienCuentaComoFallo() {
        initConCircuitoSensible();
        server.expect(ExpectedCount.times(2), requestTo(startsWith("https://clima.test/v1/forecast")))
                .andRespond(withSuccess("{\"otra\":1}", MediaType.APPLICATION_JSON));
        cliente.pronostico(0, 0, LocalDate.of(2026, 11, 1));
        cliente.pronostico(0, 0, LocalDate.of(2026, 11, 1));
        assertEquals(CircuitBreaker.State.OPEN, registro.circuitBreaker("clima").getState());
    }

    @Test
    void unError4xxNoAbreElCircuito() {
        initConCircuitoSensible();
        server.expect(ExpectedCount.times(5), requestTo(startsWith("https://clima.test/v1/forecast")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)); // p. ej. fecha fuera del rango de pronóstico
        for (int i = 0; i < 5; i++) {
            assertTrue(cliente.pronostico(0, 0, LocalDate.of(2027, 1, 1)).isEmpty());
        }
        assertEquals(CircuitBreaker.State.CLOSED, registro.circuitBreaker("clima").getState());
        server.verify();
    }

    private ActividadDto act(int edadMin, int edadMax, Nivel nivel, String estado) {
        return new ActividadDto(1L, "Trekking", 100, 0, 0, 40, 30, true, edadMin, edadMax, nivel, estado, 11, 3); // nov-mar
    }

    @Test
    void reglasDeTemporadaYClima() {
        var a = act(12, 0, Nivel.PRINCIPIANTE, "ACTIVA");
        ReservaService.validarTemporada(a, LocalDate.of(2026, 12, 10));
        ReservaService.validarTemporada(a, LocalDate.of(2027, 2, 10));
        assertThrows(ReglaNegocioException.class,
                () -> ReservaService.validarTemporada(a, LocalDate.of(2026, 6, 10)));
        assertNull(ReservaService.problemaClimatico(a, new Pronostico(40, 30)));
        assertNotNull(ReservaService.problemaClimatico(a, new Pronostico(41, 10)));
        assertNotNull(ReservaService.problemaClimatico(a, new Pronostico(0, 31)));
    }

    @Test
    void reglasDeParticipantesYEstado() {
        LocalDate f = LocalDate.of(2026, 12, 10);
        var a = act(12, 60, Nivel.INTERMEDIO, "ACTIVA");
        ReservaService.validarReglas(a, f, 12, 60, Nivel.INTERMEDIO);                     // ok
        assertThrows(ReglaNegocioException.class, () -> ReservaService.validarReglas(a, f, 11, 40, Nivel.AVANZADO));      // muy chico
        assertThrows(ReglaNegocioException.class, () -> ReservaService.validarReglas(a, f, 20, 61, Nivel.AVANZADO));      // muy grande
        assertThrows(ReglaNegocioException.class, () -> ReservaService.validarReglas(a, f, 20, 40, Nivel.PRINCIPIANTE));  // poco nivel
        var suspendida = act(0, 0, Nivel.PRINCIPIANTE, "SUSPENDIDA");
        assertThrows(ReglaNegocioException.class, () -> ReservaService.validarReglas(suspendida, f, 20, 30, Nivel.AVANZADO));
    }

    @Test
    void soloPendienteYConfirmadaRetienenCupo() {
        assertTrue(EstadoReserva.PENDIENTE.retieneCupo());
        assertTrue(EstadoReserva.CONFIRMADA.retieneCupo());
        assertFalse(EstadoReserva.A_REPROGRAMAR.retieneCupo());
        assertFalse(EstadoReserva.CANCELADA.retieneCupo());
    }
}
