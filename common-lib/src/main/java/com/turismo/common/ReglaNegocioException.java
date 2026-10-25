package com.turismo.common;

/** Pedido bien formado pero que viola una regla del dominio (temporada, edad, clima...). -> HTTP 422 */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) { super(mensaje); }
}
