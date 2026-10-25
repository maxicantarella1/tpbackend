package com.Reservas;

import com.turismo.common.AccesoDenegadoException;
import com.turismo.common.ConflictoNegocioException;
import com.turismo.common.Identidad;
import com.turismo.common.Nivel;
import com.turismo.common.ReglaNegocioException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Prueba el servicio con repositorio simulado (Mockito), Actividades simulada (MockRestServiceServer) y clima simulado. */
class ReservaServiceTest {

    private static final String BASE = "http://act.test";
    private static final LocalDate F1 = LocalDate.now().plusDays(10);
    private static final LocalDate F2 = LocalDate.now().plusDays(11);
    private static final Identidad ADMIN = new Identidad("admin", true);
    private static final Identidad ANA = new Identidad("ana", false);

    private ReservaRepository repo;
    private ClimaClient clima;
    private MockRestServiceServer server;
    private ReservaService service;

    @BeforeEach
    void init() {
        repo = mock(ReservaRepository.class);
        when(repo.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));
        clima = mock(ClimaClient.class);
        RestClient.Builder b = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(b).ignoreExpectOrder(true).build();
        service = new ReservaService(repo, b.build(), clima, CircuitBreakerRegistry.ofDefaults());
    }

    // ---------- helpers ----------
    private static String actividadJson(String estado, boolean aireLibre) {
        return "{\"id\":1,\"nombre\":\"Trekking\",\"precio\":100.0,\"latitud\":-31.4,\"longitud\":-64.2,"
                + "\"maxProbLluvia\":40,\"maxVientoKmh\":30,\"aireLibre\":" + aireLibre + ",\"edadMinima\":12,"
                + "\"edadMaxima\":0,\"nivelRequerido\":\"PRINCIPIANTE\",\"estado\":\"" + estado
                + "\",\"mesInicio\":1,\"mesFin\":12}";
    }

    private void esperaActividad(String estado, boolean aireLibre) {
        server.expect(requestTo(BASE + "/actividades/1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(actividadJson(estado, aireLibre), MediaType.APPLICATION_JSON));
    }

    private void esperaDescuento(LocalDate fecha) {
        server.expect(requestTo(BASE + "/actividades/1/salidas/" + fecha + "/cupo")).andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
    }

    private void esperaDevolucion(LocalDate fecha) {
        server.expect(requestTo(BASE + "/actividades/1/salidas/" + fecha + "/cupo/devolucion")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
    }

    private ReservaRequest req(LocalDate fecha, int personas) {
        return new ReservaRequest(1L, "ignorado", fecha, personas, 20, 35, Nivel.PRINCIPIANTE);
    }

    private Reserva reservaGuardada(Long id, String usuario, LocalDate fecha, int personas, EstadoReserva estado) {
        Reserva r = new Reserva();
        r.setId(id);
        r.setActividadId(1L);
        r.setUsuario(usuario);
        r.setFecha(fecha);
        r.setCantidadPersonas(personas);
        r.setEdadMinimaParticipante(20);
        r.setEdadMaximaParticipante(35);
        r.setNivelParticipante(Nivel.PRINCIPIANTE);
        r.setEstado(estado);
        when(repo.findById(id)).thenReturn(Optional.of(r));
        return r;
    }

    // ---------- reservar ----------
    @Test
    void reservaConfirmadaConClimaViable() {
        esperaActividad("ACTIVA", true);
        esperaDescuento(F1);
        when(clima.pronostico(anyDouble(), anyDouble(), eq(F1))).thenReturn(Optional.of(new Pronostico(10, 10)));

        Reserva r = service.reservar(req(F1, 2), ANA);

        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
        assertEquals(200.0, r.getPrecioTotal());
        assertEquals("ana", r.getUsuario(), "un USUARIO reserva a su nombre (token), no el del body");
        server.verify();
    }

    @Test
    void adminPuedeReservarANombreDeOtro() {
        esperaActividad("ACTIVA", true);
        esperaDescuento(F1);
        when(clima.pronostico(anyDouble(), anyDouble(), any())).thenReturn(Optional.of(new Pronostico(10, 10)));
        Reserva r = service.reservar(new ReservaRequest(1L, "pedro", F1, 1, 20, 35, Nivel.PRINCIPIANTE), ADMIN);
        assertEquals("pedro", r.getUsuario());
    }

    @Test
    void sinPronosticoQuedaPendiente() {
        esperaActividad("ACTIVA", true);
        esperaDescuento(F1);
        when(clima.pronostico(anyDouble(), anyDouble(), any())).thenReturn(Optional.empty());

        Reserva r = service.reservar(req(F1, 1), ANA);

        assertEquals(EstadoReserva.PENDIENTE, r.getEstado());
        assertNotNull(r.getMotivo());
        server.verify();
    }

    @Test
    void climaInviableRechazaYNoTocaElCupo() {
        esperaActividad("ACTIVA", true); // solo se espera el GET: cualquier PATCH haría fallar el test
        when(clima.pronostico(anyDouble(), anyDouble(), any())).thenReturn(Optional.of(new Pronostico(90, 10)));

        assertThrows(ReglaNegocioException.class, () -> service.reservar(req(F1, 1), ANA));

        server.verify();
        verify(repo, never()).save(any());
    }

    @Test
    void actividadAlAireLibreFalseNoConsultaElClima() {
        esperaActividad("ACTIVA", false);
        esperaDescuento(F1);
        Reserva r = service.reservar(req(F1, 1), ANA);
        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
        verifyNoInteractions(clima);
    }

    @Test
    void actividadSuspendidaNoSeReserva() {
        esperaActividad("SUSPENDIDA", true);
        assertThrows(ReglaNegocioException.class, () -> service.reservar(req(F1, 1), ANA));
        verifyNoInteractions(clima);
    }

    @Test
    void cupoAgotadoDevuelve409() {
        esperaActividad("ACTIVA", false);
        server.expect(requestTo(BASE + "/actividades/1/salidas/" + F1 + "/cupo"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        assertThrows(CupoAgotadoException.class, () -> service.reservar(req(F1, 9), ANA));
        verify(repo, never()).save(any());
    }

    @Test
    void siFallaElGuardadoSeDevuelveElCupo() {
        esperaActividad("ACTIVA", false);
        esperaDescuento(F1);
        esperaDevolucion(F1); // compensación
        when(repo.save(any(Reserva.class))).thenThrow(new IllegalStateException("BD caída"));

        assertThrows(IllegalStateException.class, () -> service.reservar(req(F1, 2), ANA));
        server.verify();
    }

    @Test
    void salidaInexistenteEs422() {
        esperaActividad("ACTIVA", false);
        server.expect(requestTo(BASE + "/actividades/1/salidas/" + F1 + "/cupo"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(ReglaNegocioException.class, () -> service.reservar(req(F1, 1), ANA));
    }

    // ---------- reevaluar ----------
    @Test
    void reevaluarClimaInviablePasaAReprogramarYLiberaCupo() {
        Reserva r = reservaGuardada(5L, "ana", F1, 2, EstadoReserva.CONFIRMADA);
        esperaActividad("ACTIVA", true);
        esperaDevolucion(F1);
        when(clima.pronostico(anyDouble(), anyDouble(), eq(F1))).thenReturn(Optional.of(new Pronostico(10, 80)));

        Reserva res = service.reevaluar(5L);

        assertEquals(EstadoReserva.A_REPROGRAMAR, res.getEstado());
        assertTrue(res.getMotivo().contains("viento"));
        server.verify();
    }

    @Test
    void reevaluarPendienteConClimaViableLaConfirma() {
        reservaGuardada(5L, "ana", F1, 2, EstadoReserva.PENDIENTE);
        esperaActividad("ACTIVA", true);
        when(clima.pronostico(anyDouble(), anyDouble(), any())).thenReturn(Optional.of(new Pronostico(10, 10)));
        assertEquals(EstadoReserva.CONFIRMADA, service.reevaluar(5L).getEstado());
    }

    @Test
    void reevaluarActividadSuspendidaPasaAReprogramar() {
        reservaGuardada(5L, "ana", F1, 2, EstadoReserva.CONFIRMADA);
        esperaActividad("SUSPENDIDA", true);
        esperaDevolucion(F1);
        assertEquals(EstadoReserva.A_REPROGRAMAR, service.reevaluar(5L).getEstado());
        server.verify();
    }

    @Test
    void reevaluarSinPronosticoEs503() {
        reservaGuardada(5L, "ana", F1, 2, EstadoReserva.PENDIENTE);
        esperaActividad("ACTIVA", true);
        when(clima.pronostico(anyDouble(), anyDouble(), any())).thenReturn(Optional.empty());
        assertThrows(com.turismo.common.ServicioNoDisponibleException.class, () -> service.reevaluar(5L));
    }

    @Test
    void reevaluarProximasNoSeDetieneSiUnaFalla() {
        Reserva a = reservaGuardada(1L, "ana", F1, 1, EstadoReserva.CONFIRMADA);
        Reserva b = reservaGuardada(2L, "ana", F1, 1, EstadoReserva.CONFIRMADA);
        when(repo.findByEstadoInAndFechaBetween(any(), any(), any())).thenReturn(List.of(a, b));
        // GET actividad falla (500) para ambas: no debe propagarse
        server.expect(requestTo(BASE + "/actividades/1")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(BASE + "/actividades/1")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertEquals(0, service.reevaluarProximas(F1, F2));
    }

    // ---------- modificar ----------
    @Test
    void modificarCambiandoFechaTomaLaNuevaYLiberaLaVieja() {
        reservaGuardada(5L, "ana", F1, 2, EstadoReserva.CONFIRMADA);
        esperaActividad("ACTIVA", false);
        esperaDescuento(F2);
        esperaDevolucion(F1);

        Reserva r = service.modificar(5L, new ModificarReservaRequest(F2, null), ANA);

        assertEquals(F2, r.getFecha());
        assertEquals(2, r.getCantidadPersonas());
        server.verify();
    }

    @Test
    void modificarMismaFechaMasPersonasTomaSoloLaDiferencia() {
        reservaGuardada(5L, "ana", F1, 2, EstadoReserva.CONFIRMADA);
        esperaActividad("ACTIVA", false);
        server.expect(requestTo(BASE + "/actividades/1/salidas/" + F1 + "/cupo")).andExpect(method(HttpMethod.PATCH))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.cantidad").value(3))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        Reserva r = service.modificar(5L, new ModificarReservaRequest(null, 5), ANA);

        assertEquals(5, r.getCantidadPersonas());
        assertEquals(500.0, r.getPrecioTotal());
        server.verify();
    }

    @Test
    void modificarDesdeAReprogramarNoLiberaNada() {
        reservaGuardada(5L, "ana", F1, 2, EstadoReserva.A_REPROGRAMAR);
        esperaActividad("ACTIVA", false);
        esperaDescuento(F2); // solo toma; no hay cupo viejo que devolver
        Reserva r = service.modificar(5L, new ModificarReservaRequest(F2, null), ANA);
        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
        server.verify();
    }

    @Test
    void modificarFallaSiLaNuevaFechaNoTieneCupoYNoPierdeLaVieja() {
        reservaGuardada(5L, "ana", F1, 2, EstadoReserva.CONFIRMADA);
        esperaActividad("ACTIVA", false);
        server.expect(requestTo(BASE + "/actividades/1/salidas/" + F2 + "/cupo"))
                .andRespond(withStatus(HttpStatus.CONFLICT)); // no se espera devolución de F1
        assertThrows(CupoAgotadoException.class, () -> service.modificar(5L, new ModificarReservaRequest(F2, null), ANA));
        server.verify();
    }

    @Test
    void noSeModificaUnaReservaCancelada() {
        reservaGuardada(5L, "ana", F1, 2, EstadoReserva.CANCELADA);
        assertThrows(ConflictoNegocioException.class, () -> service.modificar(5L, new ModificarReservaRequest(F2, null), ANA));
    }

    // ---------- cancelar / propiedad ----------
    @Test
    void cancelarDevuelveCupoYNoSePuedeCancelarDosVeces() {
        reservaGuardada(5L, "ana", F1, 2, EstadoReserva.CONFIRMADA);
        esperaDevolucion(F1);
        assertEquals(EstadoReserva.CANCELADA, service.cancelar(5L, ANA).getEstado());
        server.verify();
        assertThrows(ConflictoNegocioException.class, () -> service.cancelar(5L, ANA));
    }

    @Test
    void cancelarUnaAReprogramarNoDevuelveCupo() {
        reservaGuardada(5L, "ana", F1, 2, EstadoReserva.A_REPROGRAMAR);
        assertEquals(EstadoReserva.CANCELADA, service.cancelar(5L, ANA).getEstado());
        server.verify(); // sin llamadas
    }

    @Test
    void unUsuarioNoTocaReservasAjenas() {
        reservaGuardada(5L, "pedro", F1, 2, EstadoReserva.CONFIRMADA);
        assertThrows(AccesoDenegadoException.class, () -> service.obtenerPropia(5L, ANA));
        assertThrows(AccesoDenegadoException.class, () -> service.cancelar(5L, ANA));
        assertThrows(AccesoDenegadoException.class, () -> service.modificar(5L, new ModificarReservaRequest(F2, null), ANA));
        assertDoesNotThrow(() -> service.obtenerPropia(5L, ADMIN));
    }

    @Test
    void listarFiltraPorUsuarioSalvoAdmin() {
        when(repo.findByUsuario("ana")).thenReturn(List.of(new Reserva()));
        when(repo.findAll()).thenReturn(List.of(new Reserva(), new Reserva()));
        assertEquals(1, service.listar(ANA).size());
        assertEquals(2, service.listar(ADMIN).size());
    }

    @Test
    void conActividadesCaidaElCircuitoAbreYFallaRapido() {
        var cfg = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(2).failureRateThreshold(50)
                .waitDurationInOpenState(java.time.Duration.ofMinutes(5)).build();
        RestClient.Builder b = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(b).build();
        var svc = new ReservaService(repo, b.build(), clima, CircuitBreakerRegistry.of(cfg));
        // solo 2 llamadas reales llegan al servidor; la 3ª y 4ª las corta el circuito
        server.expect(org.springframework.test.web.client.ExpectedCount.times(2), requestTo(BASE + "/actividades/1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        for (int i = 0; i < 4; i++) {
            assertThrows(com.turismo.common.ServicioNoDisponibleException.class, () -> svc.reservar(req(F1, 1), ANA));
        }
        server.verify();
    }
}
