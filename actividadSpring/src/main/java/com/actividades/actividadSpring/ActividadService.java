package com.actividades.actividadSpring;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class ActividadService {

    private final ActividadRepository repo;

    public ActividadService(ActividadRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Actividad descontarCupo(Long id, int cantidad) {
        Actividad a = repo.findById(id)
                .orElseThrow(() -> new ActividadNoEncontradaException(id));

        if (a.getCupoDisponible() < cantidad) {
            throw new CupoInsuficienteException(id, cantidad, a.getCupoDisponible());
        }
        a.setCupoDisponible(a.getCupoDisponible() - cantidad);
        return a; // al terminar la transacción, JPA guarda el cambio solo
    }
}