package com.actividades.actividadSpring;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Actividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nombre;

    private double latitud;
    private double longitud;

    @Min(0)
    private int cupoDisponible;

    @Positive
    private double precio;

    private int maxProbLluvia;      // % máximo de prob. de lluvia tolerado
    private double maxVientoKmh;     // viento máximo tolerado

    private boolean aireLibre = true; // si es false no depende del clima

    @Min(0)
    private int edadMinima;           // edad mínima de los participantes

    @Min(1) @Max(12)
    private int mesInicio = 1;        // temporada: mes de inicio (1-12)
    @Min(1) @Max(12)
    private int mesFin = 12;          // temporada: mes de fin (puede ser < inicio: cruza el año)

}
