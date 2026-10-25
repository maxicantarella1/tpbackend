package com.Reservas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.context.annotation.Import(com.turismo.common.ManejadorErroresComun.class)
@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class BaseEjemploApplication {
	public static void main(String[] args) {
		SpringApplication.run(BaseEjemploApplication.class, args);
	}

}
