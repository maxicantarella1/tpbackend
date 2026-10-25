package com.Reservas;

/**
 * PENDIENTE: retiene cupo, falta confirmar (sin pronóstico).
 * CONFIRMADA: retiene cupo.
 * A_REPROGRAMAR: se volvió inviable (clima / actividad suspendida); NO retiene cupo; el usuario modifica la fecha o cancela.
 * CANCELADA: final; no retiene cupo.
 */
public enum EstadoReserva {
    PENDIENTE, CONFIRMADA, A_REPROGRAMAR, CANCELADA;

    public boolean retieneCupo() { return this == PENDIENTE || this == CONFIRMADA; }
}
