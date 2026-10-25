package com.actividades.actividadSpring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Una "salida" es la actividad en una fecha concreta; el cupo vive acá, no en la actividad. */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"actividadId", "fecha"}))
public class Salida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actividadId;
    private LocalDate fecha;
    private int cupoDisponible;
}
