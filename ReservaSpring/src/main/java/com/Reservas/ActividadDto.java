package com.Reservas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.turismo.common.Nivel;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ActividadDto(Long id, String nombre, double precio,
                           double latitud, double longitud,
                           int maxProbLluvia, double maxVientoKmh,
                           boolean aireLibre, int edadMinima, int edadMaxima, Nivel nivelRequerido,
                           String estado, int mesInicio, int mesFin) {

    public boolean suspendida() { return "SUSPENDIDA".equals(estado); }
}
