package com.actividades.actividadSpring;

import com.turismo.common.RecursoNoEncontradoException;

import java.time.LocalDate;

public class SalidaNoEncontradaException extends RecursoNoEncontradoException {
    public SalidaNoEncontradaException(Long actividadId, LocalDate fecha) {
        super("La actividad " + actividadId + " no tiene salida el " + fecha);
    }
}
