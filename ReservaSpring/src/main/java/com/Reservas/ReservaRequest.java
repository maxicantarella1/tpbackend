package com.Reservas;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// Lo que el cliente manda al crear una reserva (no expone la entidad ni el estado)
public record ReservaRequest(
        @NotNull Long actividadId,
        @NotBlank String usuario,
        @NotNull @FutureOrPresent LocalDate fecha,
        @Min(1) int cantidadPersonas) { }
