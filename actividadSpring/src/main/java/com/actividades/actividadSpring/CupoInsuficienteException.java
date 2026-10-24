package com.actividades.actividadSpring;

import com.turismo.common.ConflictoNegocioException;

public class CupoInsuficienteException extends ConflictoNegocioException {
    public CupoInsuficienteException(Long id, int pedido) {
        super("Actividad " + id + ": no hay cupo suficiente para " + pedido + " lugares");
    }
}
