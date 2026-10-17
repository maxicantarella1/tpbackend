package com.Reservas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ActividadDto(Long id, String nombre, int cupoDisponible,
                           double latitud, double longitud,
                           int maxProbLluvia, double maxVientoKmh) { }
