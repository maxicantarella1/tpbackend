package com.actividades.actividadSpring;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ActividadService {

    private final ActividadRepository repo;

    public ActividadService(ActividadRepository repo) {
        this.repo = repo;
    }

    public List<Actividad> listar() { return repo.findAll(); }

    public Actividad obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new ActividadNoEncontradaException(id));
    }

    public Actividad crear(Actividad a) { return repo.save(a); }

    public void borrar(Long id) {
        obtener(id);
        repo.deleteById(id);
    }

    /** Descuento atómico: evita que dos reservas simultáneas consuman el mismo cupo. */
    @Transactional
    public void descontarCupo(Long id, int cantidad) {
        if (repo.descontar(id, cantidad) == 0) {
            obtener(id); // si no existe -> 404
            throw new CupoInsuficienteException(id, cantidad); // existe pero no alcanza -> 409
        }
    }

    /** Compensación: Reservas la usa si falla después de haber descontado. */
    @Transactional
    public void devolverCupo(Long id, int cantidad) {
        if (repo.devolver(id, cantidad) == 0) throw new ActividadNoEncontradaException(id);
    }
}
