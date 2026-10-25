package com.turismo.common;

/** Contrato de cabeceras entre el Gateway y los microservicios (zona de confianza). */
public final class Seguridad {
    private Seguridad() { }

    /** Secreto compartido: prueba que la llamada viene del Gateway (o de otro servicio de la zona). */
    public static final String HEADER_SECRET = "X-Internal-Secret";
    /** Usuario autenticado (claim preferred_username del JWT). Lo pone SIEMPRE el Gateway. */
    public static final String HEADER_USER = "X-User-Name";
    /** Roles del usuario separados por coma (ADMIN,USUARIO). Lo pone SIEMPRE el Gateway. */
    public static final String HEADER_ROLES = "X-User-Roles";
}
