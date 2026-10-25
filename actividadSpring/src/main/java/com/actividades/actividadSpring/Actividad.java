package com.actividades.actividadSpring;

import com.turismo.common.Nivel;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull
    private Long proveedorId;            // proveedor turístico que la ofrece

    @Enumerated(EnumType.STRING)
    private EstadoActividad estado = EstadoActividad.ACTIVA; // el proveedor puede suspenderla

    private double latitud;              // ubicación (se usa para consultar el clima)
    private double longitud;

    @Min(1)
    private int capacidadPorSalida;      // cupo con que arranca cada salida (fecha)

    @Positive
    private double precio;               // por persona

    private boolean aireLibre = true;    // si es false no depende del clima
    private int maxProbLluvia;           // % máximo de prob. de lluvia tolerado
    private double maxVientoKmh;         // viento máximo tolerado

    @Min(0)
    private int edadMinima;              // participantes
    @Min(0)
    private int edadMaxima;              // 0 = sin límite

    @Enumerated(EnumType.STRING)
    private Nivel nivelRequerido = Nivel.PRINCIPIANTE;

    @Min(1) @Max(12)
    private int mesInicio = 1;           // temporada
    @Min(1) @Max(12)
    private int mesFin = 12;             // puede ser < inicio: cruza el año
}
