package com.Reservas;

import com.turismo.common.RecursoNoEncontradoException;

public class ActividadInexistenteException extends RecursoNoEncontradoException {
    public ActividadInexistenteException(Long id) {
        super("No existe la actividad " + id);
    }
}
