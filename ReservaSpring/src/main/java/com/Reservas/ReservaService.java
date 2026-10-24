package com.Reservas;

import com.turismo.common.ConflictoNegocioException;
import com.turismo.common.ReglaNegocioException;
import com.turismo.common.ServicioNoDisponibleException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReservaService {

    private final ReservaRepository repo;
    private final RestClient actividades;
    private final ClimaClient clima;

    public ReservaService(ReservaRepository repo, RestClient actividadesClient, ClimaClient clima) {
        this.repo = repo;
        this.actividades = actividadesClient;
        this.clima = clima;
    }

    public Reserva reservar(ReservaRequest req) {
        // 1. La actividad existe (Actividades es la dueña del dato)
        ActividadDto actividad = obtenerActividad(req.actividadId());

        // 2. Reglas del dominio: se validan ANTES de tocar el cupo
        validarTemporada(actividad, req.fecha());
        if (req.edadMinimaParticipante() < actividad.edadMinima()) {
            throw new ReglaNegocioException("La actividad exige edad mínima " + actividad.edadMinima()
                    + " y el participante más joven tiene " + req.edadMinimaParticipante());
        }

        // 3. Clima (API externa): decide si la actividad al aire libre puede realizarse ese día
        EstadoReserva estado = EstadoReserva.CONFIRMADA;
        String motivo = null;
        if (actividad.aireLibre()) {
            Optional<Pronostico> p = clima.pronostico(actividad.latitud(), actividad.longitud(), req.fecha());
            if (p.isPresent()) {
                String problema = problemaClimatico(actividad, p.get());
                if (problema != null) {
                    throw new ReglaNegocioException("Actividad inviable ese día: " + problema);
                }
            } else {
                // Sin pronóstico (fecha lejana o API caída): no confirmamos a ciegas
                estado = EstadoReserva.PENDIENTE;
                motivo = "Sin pronóstico disponible; se confirma al reevaluar el clima";
            }
        }

        // 4. Descuento de cupo (atómico, lo decide Actividades)
        descontarCupo(actividad.id(), req.cantidadPersonas());

        // 5. Guardo mi reserva; si falla, compenso devolviendo el cupo
        Reserva r = new Reserva();
        r.setActividadId(actividad.id());
        r.setUsuario(req.usuario());
        r.setFecha(req.fecha());
        r.setCantidadPersonas(req.cantidadPersonas());
        r.setEstado(estado);
        r.setMotivo(motivo);
        try {
            return repo.save(r);
        } catch (RuntimeException e) {
            devolverCupo(actividad.id(), req.cantidadPersonas());
            throw e;
        }
    }

    /** Una reserva PENDIENTE (o confirmada) puede volverse viable o inviable al cambiar el pronóstico. */
    public Reserva reevaluar(Long id) {
        Reserva r = obtener(id);
        if (r.getEstado() == EstadoReserva.CANCELADA) {
            throw new ConflictoNegocioException("La reserva " + id + " está cancelada");
        }
        ActividadDto actividad = obtenerActividad(r.getActividadId());
        if (!actividad.aireLibre()) return r;

        Pronostico p = clima.pronostico(actividad.latitud(), actividad.longitud(), r.getFecha())
                .orElseThrow(() -> new ServicioNoDisponibleException("clima"));
        String problema = problemaClimatico(actividad, p);
        if (problema == null) {
            r.setEstado(EstadoReserva.CONFIRMADA);
            r.setMotivo(null);
        } else {
            devolverCupo(r.getActividadId(), r.getCantidadPersonas());
            r.setEstado(EstadoReserva.CANCELADA);
            r.setMotivo("Cancelada por clima: " + problema);
        }
        return repo.save(r);
    }

    public Reserva cancelar(Long id) {
        Reserva r = obtener(id);
        if (r.getEstado() == EstadoReserva.CANCELADA) {
            throw new ConflictoNegocioException("La reserva " + id + " ya estaba cancelada");
        }
        devolverCupo(r.getActividadId(), r.getCantidadPersonas());
        r.setEstado(EstadoReserva.CANCELADA);
        r.setMotivo("Cancelada por el usuario");
        return repo.save(r);
    }

    public List<Reserva> listar() {
        return repo.findAll();
    }

    public Reserva obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new ReservaNoEncontradaException(id));
    }

    // ---------- reglas ----------

    static void validarTemporada(ActividadDto a, LocalDate fecha) {
        int m = fecha.getMonthValue();
        boolean enTemporada = a.mesInicio() <= a.mesFin()
                ? (m >= a.mesInicio() && m <= a.mesFin())
                : (m >= a.mesInicio() || m <= a.mesFin()); // temporada que cruza el año
        if (!enTemporada) {
            throw new ReglaNegocioException("La actividad no se ofrece en el mes " + m
                    + " (temporada " + a.mesInicio() + " a " + a.mesFin() + ")");
        }
    }

    static String problemaClimatico(ActividadDto a, Pronostico p) {
        if (p.probLluvia() > a.maxProbLluvia()) {
            return "probabilidad de lluvia " + p.probLluvia() + "% (máximo " + a.maxProbLluvia() + "%)";
        }
        if (p.vientoKmh() > a.maxVientoKmh()) {
            return "viento " + p.vientoKmh() + " km/h (máximo " + a.maxVientoKmh() + " km/h)";
        }
        return null;
    }

    // ---------- llamadas a Actividades ----------

    private ActividadDto obtenerActividad(Long id) {
        try {
            return actividades.get().uri("/actividades/{id}", id).retrieve().body(ActividadDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ActividadInexistenteException(id);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new ServicioNoDisponibleException("actividades-service");
        }
    }

    private void devolverCupo(Long id, int cantidad) {
        try {
            actividades.post().uri("/actividades/{id}/cupo/devolucion", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cantidad", cantidad))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            // best-effort: se registra para conciliar a mano; no tapa el error original
            System.err.println("No se pudo devolver el cupo de la actividad " + id + ": " + ex.getMessage());
        }
    }

    private void descontarCupo(Long id, int cantidad) {
        try {
            actividades.patch().uri("/actividades/{id}/cupo", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cantidad", cantidad))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            throw new CupoAgotadoException(id);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ActividadInexistenteException(id);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new ServicioNoDisponibleException("actividades-service");
        }
    }
}
