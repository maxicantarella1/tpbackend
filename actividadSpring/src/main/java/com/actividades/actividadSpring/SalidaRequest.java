package com.actividades.actividadSpring;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** cupo es opcional: si no viene se usa la capacidadPorSalida de la actividad. */
public record SalidaRequest(@NotNull @FutureOrPresent LocalDate fecha, @Min(1) Integer cupo) { }
