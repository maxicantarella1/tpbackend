package com.actividades.actividadSpring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActividadRepository extends JpaRepository<Actividad, Long> {

    // UPDATE atómico en la base: descuenta solo si alcanza. Devuelve filas afectadas (0 = no alcanzó o no existe).
    @Modifying(clearAutomatically = true)
    @Query("update Actividad a set a.cupoDisponible = a.cupoDisponible - :n "
            + "where a.id = :id and a.cupoDisponible >= :n")
    int descontar(@Param("id") Long id, @Param("n") int n);

    @Modifying(clearAutomatically = true)
    @Query("update Actividad a set a.cupoDisponible = a.cupoDisponible + :n where a.id = :id")
    int devolver(@Param("id") Long id, @Param("n") int n);
}
