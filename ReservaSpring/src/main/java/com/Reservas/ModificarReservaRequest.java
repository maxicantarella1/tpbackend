package com.Reservas;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

/** Campos opcionales: solo se cambia lo que viene. */
public record ModificarReservaRequest(@FutureOrPresent LocalDate fecha, @Min(1) Integer cantidadPersonas) { }
