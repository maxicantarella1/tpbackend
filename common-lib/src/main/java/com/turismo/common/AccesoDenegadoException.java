package com.turismo.common;

/** El usuario está autenticado pero no puede operar sobre ese recurso (ej. reserva de otro). -> HTTP 403 */
public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException(String mensaje) { super(mensaje); }
}
