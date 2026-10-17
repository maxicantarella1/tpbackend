package com.Reservas;

import com.turismo.common.ConflictoNegocioException;
import com.turismo.common.RecursoNoEncontradoException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import com.turismo.common.ServicioNoDisponibleException;

import java.util.List;
import java.util.Map;


@Service
public class ReservaService {

    private final ReservaRepository repo;
    private final RestClient actividades;

    public ReservaService(ReservaRepository repo, RestClient actividadesClient) {
        this.repo = repo;
        this.actividades = actividadesClient;
    }

    public Reserva reservar(ReservaRequest req) {
        // 1. Verifico que la actividad exista (más adelante: acá se consulta el clima)
        ActividadDto actividad = obtenerActividad(req.actividadId());

        // 2. Pido a Actividades que descuente el cupo (ella decide si alcanza)
        descontarCupo(actividad.id(), req.cantidadPersonas());

        // 3. Recién ahora guardo mi reserva
        Reserva r = new Reserva();
        r.setActividadId(actividad.id());
        r.setUsuario(req.usuario());
        r.setFecha(req.fecha());
        r.setCantidadPersonas(req.cantidadPersonas());
        r.setEstado(EstadoReserva.CONFIRMADA);
        return repo.save(r);
    }

    public List<Reserva> listar() {
        return repo.findAll();
    }

    public Reserva obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new ReservaNoEncontradaException(id));
    }

    private ActividadDto obtenerActividad(Long id) {
        try {
            return actividades.get()
                    .uri("/actividades/{id}", id)
                    .retrieve()
                    .body(ActividadDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ActividadInexistenteException(id);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new ServicioNoDisponibleException("actividades-service");
        }
    }

    private void descontarCupo(Long id, int cantidad) {
        try {
            actividades.patch()
                    .uri("/actividades/{id}/cupo", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cantidad", cantidad))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            throw new CupoAgotadoException(id);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ActividadInexistenteException(id);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new ServicioNoDisponibleException("actividades-service");
        }
    }
}
