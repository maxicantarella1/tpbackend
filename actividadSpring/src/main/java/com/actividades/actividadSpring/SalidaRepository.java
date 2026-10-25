package com.actividades.actividadSpring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalidaRepository extends JpaRepository<Salida, Long> {

    Optional<Salida> findByActividadIdAndFecha(Long actividadId, LocalDate fecha);
    boolean existsByActividadIdAndFecha(Long actividadId, LocalDate fecha);
    boolean existsByActividadId(Long actividadId);
    List<Salida> findByActividadIdOrderByFecha(Long actividadId);

    // UPDATE atómico: descuenta solo si alcanza. Devuelve filas afectadas (0 = no alcanza o no existe la salida).
    @Modifying(clearAutomatically = true)
    @Query("update Salida s set s.cupoDisponible = s.cupoDisponible - :n "
            + "where s.actividadId = :a and s.fecha = :f and s.cupoDisponible >= :n")
    int descontar(@Param("a") Long actividadId, @Param("f") LocalDate fecha, @Param("n") int n);

    @Modifying(clearAutomatically = true)
    @Query("update Salida s set s.cupoDisponible = s.cupoDisponible + :n "
            + "where s.actividadId = :a and s.fecha = :f")
    int devolver(@Param("a") Long actividadId, @Param("f") LocalDate fecha, @Param("n") int n);
}
