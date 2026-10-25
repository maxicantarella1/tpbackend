package com.actividades.actividadSpring;

import com.turismo.common.ConflictoNegocioException;

import java.time.LocalDate;

public class CupoInsuficienteException extends ConflictoNegocioException {
    public CupoInsuficienteException(Long id, LocalDate fecha, int pedido) {
        super("Actividad " + id + " el " + fecha + ": no hay cupo suficiente para " + pedido + " lugares");
    }
}
