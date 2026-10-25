package com.Reservas;

import com.turismo.common.AccesoDenegadoException;
import com.turismo.common.ConflictoNegocioException;
import com.turismo.common.Identidad;
import com.turismo.common.Nivel;
import com.turismo.common.ReglaNegocioException;
import com.turismo.common.ServicioNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReservaService {

    private static final Logger log = LoggerFactory.getLogger(ReservaService.class);

    private final ReservaRepository repo;
    private final RestClient actividades;
    private final ClimaClient clima;

    public ReservaService(ReservaRepository repo, RestClient actividadesClient, ClimaClient clima) {
        this.repo = repo;
        this.actividades = actividadesClient;
        this.clima = clima;
    }

    // ================= casos de uso =================

    public Reserva reservar(ReservaRequest req, Identidad quien) {
        if (req.edadMaximaParticipante() < req.edadMinimaParticipante()) {
            throw new ReglaNegocioException("edadMaximaParticipante no puede ser menor que edadMinimaParticipante");
        }
        // un USUARIO solo reserva a su nombre (se toma del token, no del body); un ADMIN puede reservar a nombre de otro
        String usuario = (!quien.admin() && quien.usuario() != null) ? quien.usuario() : req.usuario();
        if (usuario == null || usuario.isBlank()) {
            throw new ReglaNegocioException("usuario es obligatorio");
        }
        ActividadDto a = obtenerActividad(req.actividadId());

        validarReglas(a, req.fecha(), req.edadMinimaParticipante(), req.edadMaximaParticipante(), req.nivelParticipante());
        EstadoReserva estado = estadoSegunClima(a, req.fecha());

        descontarCupo(a.id(), req.fecha(), req.cantidadPersonas());

        Reserva r = new Reserva();
        r.setActividadId(a.id());
        r.setUsuario(usuario);
        r.setFecha(req.fecha());
        r.setCantidadPersonas(req.cantidadPersonas());
        r.setPrecioTotal(a.precio() * req.cantidadPersonas());
        r.setEdadMinimaParticipante(req.edadMinimaParticipante());
        r.setEdadMaximaParticipante(req.edadMaximaParticipante());
        r.setNivelParticipante(req.nivelParticipante());
        aplicarEstado(r, estado);
        try {
            return repo.save(r);
        } catch (RuntimeException e) {
            devolverCupo(a.id(), req.fecha(), req.cantidadPersonas()); // compensación
            throw e;
        }
    }

    /**
     * Modifica fecha y/o cantidad. Se revalidan TODAS las reglas para los nuevos valores.
     * Primero se toma el cupo nuevo y recién después se libera el viejo, así nunca se pierde el lugar.
     */
    public Reserva modificar(Long id, ModificarReservaRequest req, Identidad quien) {
        Reserva r = obtenerPropia(id, quien);
        if (r.getEstado() == EstadoReserva.CANCELADA) {
            throw new ConflictoNegocioException("La reserva " + id + " está cancelada y no se puede modificar");
        }
        LocalDate fechaVieja = r.getFecha();
        int cantVieja = r.getCantidadPersonas();
        LocalDate fechaNueva = req.fecha() != null ? req.fecha() : fechaVieja;
        int cantNueva = req.cantidadPersonas() != null ? req.cantidadPersonas() : cantVieja;
        boolean teniaCupo = r.getEstado().retieneCupo();
        if (fechaNueva.equals(fechaVieja) && cantNueva == cantVieja && teniaCupo) return r; // nada que hacer

        ActividadDto a = obtenerActividad(r.getActividadId());
        validarReglas(a, fechaNueva, r.getEdadMinimaParticipante(), r.getEdadMaximaParticipante(), r.getNivelParticipante());
        EstadoReserva estadoNuevo = estadoSegunClima(a, fechaNueva);

        // qué hay que tomar y qué hay que soltar de cada salida
        LocalDate tomarFecha = null, soltarFecha = null;
        int tomar = 0, soltar = 0;
        if (teniaCupo && fechaNueva.equals(fechaVieja)) {
            int delta = cantNueva - cantVieja;
            if (delta > 0) { tomarFecha = fechaNueva; tomar = delta; }
            if (delta < 0) { soltarFecha = fechaVieja; soltar = -delta; }
        } else {
            tomarFecha = fechaNueva; tomar = cantNueva;
            if (teniaCupo) { soltarFecha = fechaVieja; soltar = cantVieja; }
        }

        if (tomar > 0) descontarCupo(a.id(), tomarFecha, tomar);
        r.setFecha(fechaNueva);
        r.setCantidadPersonas(cantNueva);
        r.setPrecioTotal(a.precio() * cantNueva);
        aplicarEstado(r, estadoNuevo);
        Reserva guardada;
        try {
            guardada = repo.save(r);
        } catch (RuntimeException e) {
            if (tomar > 0) devolverCupo(a.id(), tomarFecha, tomar);
            throw e;
        }
        if (soltar > 0) devolverCupo(a.id(), soltarFecha, soltar);
        return guardada;
    }

    public Reserva cancelar(Long id, Identidad quien) {
        Reserva r = obtenerPropia(id, quien);
        if (r.getEstado() == EstadoReserva.CANCELADA) {
            throw new ConflictoNegocioException("La reserva " + id + " ya estaba cancelada");
        }
        if (r.getEstado().retieneCupo()) {
            devolverCupo(r.getActividadId(), r.getFecha(), r.getCantidadPersonas());
        }
        r.setEstado(EstadoReserva.CANCELADA);
        r.setMotivo("Cancelada por el usuario");
        return repo.save(r);
    }

    /** Una reserva válida puede volverse inviable (clima, actividad suspendida): pasa a A_REPROGRAMAR y libera el cupo. */
    public Reserva reevaluar(Long id) {
        Reserva r = obtener(id);
        if (r.getEstado() == EstadoReserva.CANCELADA) {
            throw new ConflictoNegocioException("La reserva " + id + " está cancelada");
        }
        if (r.getEstado() == EstadoReserva.A_REPROGRAMAR) return r; // ya espera una nueva fecha

        ActividadDto a = obtenerActividad(r.getActividadId());
        if (a.suspendida()) {
            return pasarAReprogramar(r, "la actividad fue suspendida por el proveedor");
        }
        if (!a.aireLibre()) return r;

        Pronostico p = clima.pronostico(a.latitud(), a.longitud(), r.getFecha())
                .orElseThrow(() -> new ServicioNoDisponibleException("clima"));
        String problema = problemaClimatico(a, p);
        if (problema != null) {
            return pasarAReprogramar(r, problema);
        }
        aplicarEstado(r, EstadoReserva.CONFIRMADA);
        return repo.save(r);
    }

    /** Lo llama la tarea programada. Un error en una reserva no frena a las demás. */
    public int reevaluarProximas(LocalDate desde, LocalDate hasta) {
        List<Reserva> candidatas = repo.findByEstadoInAndFechaBetween(
                EnumSet.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA), desde, hasta);
        int ok = 0;
        for (Reserva r : candidatas) {
            try {
                reevaluar(r.getId());
                ok++;
            } catch (RuntimeException e) {
                log.warn("No se pudo reevaluar la reserva {}: {}", r.getId(), e.getMessage());
            }
        }
        return ok;
    }

    public List<Reserva> listar(Identidad quien) {
        return (quien.admin() || quien.usuario() == null) ? repo.findAll() : repo.findByUsuario(quien.usuario());
    }

    /** Un USUARIO solo ve/modifica/cancela SUS reservas; ADMIN y llamadas internas sin usuario, todas. */
    public Reserva obtenerPropia(Long id, Identidad quien) {
        Reserva r = obtener(id);
        if (!quien.admin() && quien.usuario() != null && !quien.usuario().equals(r.getUsuario())) {
            throw new AccesoDenegadoException("La reserva " + id + " pertenece a otro usuario");
        }
        return r;
    }

    public Reserva obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new ReservaNoEncontradaException(id));
    }

    // ================= reglas (estáticas para poder testearlas sin red) =================

    static void validarReglas(ActividadDto a, LocalDate fecha, int edadMin, int edadMax, Nivel nivel) {
        if (a.suspendida()) {
            throw new ReglaNegocioException("La actividad " + a.id() + " está suspendida por el proveedor");
        }
        validarTemporada(a, fecha);
        if (edadMin < a.edadMinima()) {
            throw new ReglaNegocioException("La actividad exige edad mínima " + a.edadMinima()
                    + " y el participante más joven tiene " + edadMin);
        }
        if (a.edadMaxima() > 0 && edadMax > a.edadMaxima()) {
            throw new ReglaNegocioException("La actividad admite hasta " + a.edadMaxima()
                    + " años y hay un participante de " + edadMax);
        }
        if (a.nivelRequerido() != null && nivel.compareTo(a.nivelRequerido()) < 0) {
            throw new ReglaNegocioException("La actividad requiere nivel " + a.nivelRequerido()
                    + " y el grupo es " + nivel);
        }
    }

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

    // ================= auxiliares =================

    /** Al crear/modificar: viable -> CONFIRMADA; sin pronóstico -> PENDIENTE; inviable -> 422. */
    private EstadoReserva estadoSegunClima(ActividadDto a, LocalDate fecha) {
        if (!a.aireLibre()) return EstadoReserva.CONFIRMADA;
        Optional<Pronostico> p = clima.pronostico(a.latitud(), a.longitud(), fecha);
        if (p.isEmpty()) return EstadoReserva.PENDIENTE;
        String problema = problemaClimatico(a, p.get());
        if (problema != null) throw new ReglaNegocioException("Actividad inviable ese día: " + problema);
        return EstadoReserva.CONFIRMADA;
    }

    private void aplicarEstado(Reserva r, EstadoReserva estado) {
        r.setEstado(estado);
        r.setMotivo(estado == EstadoReserva.PENDIENTE
                ? "Sin pronóstico disponible; se confirma al reevaluar el clima" : null);
    }

    private Reserva pasarAReprogramar(Reserva r, String motivo) {
        devolverCupo(r.getActividadId(), r.getFecha(), r.getCantidadPersonas());
        r.setEstado(EstadoReserva.A_REPROGRAMAR);
        r.setMotivo("Inviable: " + motivo + ". Modifique la fecha o cancele.");
        return repo.save(r);
    }

    // ================= llamadas a Actividades =================

    private ActividadDto obtenerActividad(Long id) {
        try {
            return actividades.get().uri("/actividades/{id}", id).retrieve().body(ActividadDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ActividadInexistenteException(id);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new ServicioNoDisponibleException("actividades-service");
        }
    }

    private void descontarCupo(Long id, LocalDate fecha, int cantidad) {
        try {
            actividades.patch().uri("/actividades/{id}/salidas/{fecha}/cupo", id, fecha)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cantidad", cantidad))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            throw new CupoAgotadoException(id);
        } catch (HttpClientErrorException.NotFound e) {
            // la actividad ya se verificó antes: lo que no existe es la salida de ese día
            throw new ReglaNegocioException("No hay salida programada de la actividad " + id + " el " + fecha);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new ServicioNoDisponibleException("actividades-service");
        }
    }

    private void devolverCupo(Long id, LocalDate fecha, int cantidad) {
        try {
            actividades.post().uri("/actividades/{id}/salidas/{fecha}/cupo/devolucion", id, fecha)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cantidad", cantidad))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            // best-effort: se registra para conciliar a mano; no tapa el error original
            log.error("No se pudo devolver cupo: actividad {} fecha {} cantidad {}: {}", id, fecha, cantidad, ex.getMessage());
        }
    }
}
