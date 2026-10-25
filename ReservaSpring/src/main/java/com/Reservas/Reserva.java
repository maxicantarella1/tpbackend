package com.Reservas;

import com.turismo.common.Nivel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Data
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actividadId; // referencia al otro servicio (sin FK)
    private String usuario;
    private LocalDate fecha;
    private int cantidadPersonas;
    private double precioTotal;

    // características de los participantes (se guardan para revalidar al modificar)
    private int edadMinimaParticipante;
    private int edadMaximaParticipante;
    @Enumerated(EnumType.STRING)
    private Nivel nivelParticipante;

    private String motivo; // por qué está pendiente / a reprogramar / cancelada

    @Enumerated(EnumType.STRING)
    private EstadoReserva estado;
}
