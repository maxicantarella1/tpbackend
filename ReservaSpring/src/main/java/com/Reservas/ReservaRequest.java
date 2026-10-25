package com.Reservas;

import com.turismo.common.Nivel;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// Lo que el cliente manda al crear una reserva (no expone la entidad ni el estado)
public record ReservaRequest(
        @NotNull Long actividadId,
        String usuario,                        // se ignora si el pedido viene de un USUARIO: se toma del token
        @NotNull @FutureOrPresent LocalDate fecha,
        @Min(1) int cantidadPersonas,
        @Min(0) int edadMinimaParticipante,   // edad del participante más joven
        @Min(0) int edadMaximaParticipante,   // edad del participante de más edad
        @NotNull Nivel nivelParticipante) { }  // experiencia del grupo (la del menos experimentado)
