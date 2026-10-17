package com.actividades.actividadSpring;

import com.actividades.actividadSpring.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActividadRepository extends JpaRepository<Actividad, Long> { }
