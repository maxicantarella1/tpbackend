package com.Reservas;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    List<Reserva> findByUsuario(String usuario);
    List<Reserva> findByEstadoInAndFechaBetween(Collection<EstadoReserva> estados, LocalDate desde, LocalDate hasta);
}
