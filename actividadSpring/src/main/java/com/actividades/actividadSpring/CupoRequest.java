package com.actividades.actividadSpring;

import jakarta.validation.constraints.Min;

public record CupoRequest(@Min(1) int cantidad) { }
