package com.turismo.common;

import java.util.Arrays;

/** Quién hace el pedido, según lo que informó el Gateway. usuario == null: llamada interna sin usuario. */
public record Identidad(String usuario, boolean admin) {

    public static Identidad desde(String usuario, String roles) {
        boolean admin = roles != null && Arrays.stream(roles.split(","))
                .map(String::trim).anyMatch("ADMIN"::equals);
        return new Identidad(usuario, admin);
    }

    public static Identidad sistema() { return new Identidad(null, true); }
}
