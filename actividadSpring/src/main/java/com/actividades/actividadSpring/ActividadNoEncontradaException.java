package com.actividades.actividadSpring;

import com.turismo.common.RecursoNoEncontradoException;

public class ActividadNoEncontradaException extends RecursoNoEncontradoException {
    public ActividadNoEncontradaException(Long id) { super("No existe la actividad " + id); }
}
