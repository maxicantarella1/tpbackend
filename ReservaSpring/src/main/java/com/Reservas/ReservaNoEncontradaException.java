package com.Reservas;

import com.turismo.common.RecursoNoEncontradoException;

public class ReservaNoEncontradaException extends RecursoNoEncontradoException {
    public ReservaNoEncontradaException(Long id) {
        super("No existe la reserva " + id);
    }
}
