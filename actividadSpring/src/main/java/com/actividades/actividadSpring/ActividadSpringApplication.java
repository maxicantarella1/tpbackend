package com.actividades.actividadSpring;

import com.turismo.common.ManejadorErroresComun;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ManejadorErroresComun.class)
public class ActividadSpringApplication {

	public static void main(String[] args) {
		SpringApplication.run(ActividadSpringApplication.class, args);
	}
}