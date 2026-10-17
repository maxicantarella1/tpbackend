package com.Reservas;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Data
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actividadId; //Ref a actividad
    private String usuario;
    private LocalDate fecha;
    private int cantidadPersonas;

    @Enumerated(EnumType.STRING)
    private EstadoReserva estado; // PENDIENTE, CONFIRMADA, CANCELADA
}

