package com.Reservas;

import com.turismo.common.ConflictoNegocioException;

public class CupoAgotadoException extends ConflictoNegocioException {
    public CupoAgotadoException(Long id) {
        super("No hay cupo suficiente en la actividad " + id);
    }
}
