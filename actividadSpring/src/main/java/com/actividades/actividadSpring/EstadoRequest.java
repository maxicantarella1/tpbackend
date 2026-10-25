package com.actividades.actividadSpring;

import jakarta.validation.constraints.NotNull;

public record EstadoRequest(@NotNull EstadoActividad estado) { }
