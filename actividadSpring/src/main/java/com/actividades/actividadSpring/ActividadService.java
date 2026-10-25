package com.actividades.actividadSpring;

import com.turismo.common.ConflictoNegocioException;
import com.turismo.common.RecursoNoEncontradoException;
import com.turismo.common.ReglaNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ActividadService {

    private final ActividadRepository repo;
    private final ProveedorRepository proveedores;
    private final SalidaRepository salidas;

    public ActividadService(ActividadRepository repo, ProveedorRepository proveedores, SalidaRepository salidas) {
        this.repo = repo;
        this.proveedores = proveedores;
        this.salidas = salidas;
    }

    // ---------- proveedores ----------
    public List<Proveedor> listarProveedores() { return proveedores.findAll(); }

    public Proveedor obtenerProveedor(Long id) {
        return proveedores.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el proveedor " + id));
    }

    public Proveedor crearProveedor(Proveedor p) { p.setId(null); return proveedores.save(p); }

    // ---------- actividades ----------
    public List<Actividad> listar() { return repo.findAll(); }

    public Actividad obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new ActividadNoEncontradaException(id));
    }

    public Actividad crear(Actividad a) {
        validar(a);
        a.setId(null);
        return repo.save(a);
    }

    @Transactional
    public Actividad actualizar(Long id, Actividad nueva) {
        Actividad a = obtener(id);
        validar(nueva);
        nueva.setId(a.getId());
        return repo.save(nueva);
    }

    @Transactional
    public Actividad cambiarEstado(Long id, EstadoActividad estado) {
        Actividad a = obtener(id);
        a.setEstado(estado);
        return repo.save(a);
    }

    public void borrar(Long id) {
        obtener(id);
        if (salidas.existsByActividadId(id)) {
            throw new ConflictoNegocioException("La actividad " + id + " tiene salidas; suspéndala en vez de borrarla");
        }
        repo.deleteById(id);
    }

    private void validar(Actividad a) {
        Proveedor p = obtenerProveedor(a.getProveedorId());
        if (!p.isActivo()) throw new ReglaNegocioException("El proveedor " + p.getId() + " está inactivo");
        if (a.getEdadMaxima() > 0 && a.getEdadMaxima() < a.getEdadMinima()) {
            throw new ReglaNegocioException("edadMaxima no puede ser menor que edadMinima");
        }
    }

    // ---------- salidas ----------
    @Transactional
    public Salida crearSalida(Long actividadId, SalidaRequest req) {
        Actividad a = obtener(actividadId);
        if (salidas.existsByActividadIdAndFecha(actividadId, req.fecha())) {
            throw new ConflictoNegocioException("Ya existe una salida de la actividad " + actividadId + " el " + req.fecha());
        }
        Salida s = new Salida();
        s.setActividadId(actividadId);
        s.setFecha(req.fecha());
        s.setCupoDisponible(req.cupo() != null ? req.cupo() : a.getCapacidadPorSalida());
        return salidas.save(s);
    }

    public List<Salida> listarSalidas(Long actividadId) {
        obtener(actividadId);
        return salidas.findByActividadIdOrderByFecha(actividadId);
    }

    public Salida obtenerSalida(Long actividadId, LocalDate fecha) {
        obtener(actividadId);
        return salidas.findByActividadIdAndFecha(actividadId, fecha)
                .orElseThrow(() -> new SalidaNoEncontradaException(actividadId, fecha));
    }

    /** Descuento atómico: evita que dos reservas simultáneas consuman el mismo cupo. */
    @Transactional
    public void descontarCupo(Long actividadId, LocalDate fecha, int cantidad) {
        if (salidas.descontar(actividadId, fecha, cantidad) == 0) {
            obtenerSalida(actividadId, fecha); // 404 si no existe la actividad o la salida
            throw new CupoInsuficienteException(actividadId, fecha, cantidad); // existe pero no alcanza -> 409
        }
    }

    /** Compensación / cancelación: Reservas devuelve lugares de esa salida. */
    @Transactional
    public void devolverCupo(Long actividadId, LocalDate fecha, int cantidad) {
        if (salidas.devolver(actividadId, fecha, cantidad) == 0) {
            throw new SalidaNoEncontradaException(actividadId, fecha);
        }
    }
}
