package com.actividades.actividadSpring;

import jakarta.validation.constraints.Min;

public class CupoInsuficienteException extends RuntimeException {
    public CupoInsuficienteException(Long id, int pedido, int disponible) {
        super("Actividad " + id + ": se piden " + pedido + " lugares y hay " + disponible);
    }
}

