package com.Reservas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Tarea programada: revisa periódicamente las reservas próximas y pasa a A_REPROGRAMAR las que se volvieron inviables. */
@Component
public class ReevaluadorClima {

    private static final Logger log = LoggerFactory.getLogger(ReevaluadorClima.class);

    private final ReservaService service;
    private final int diasAdelante;

    public ReevaluadorClima(ReservaService service, @Value("${reservas.reevaluacion.dias:7}") int diasAdelante) {
        this.service = service;
        this.diasAdelante = diasAdelante;
    }

    @Scheduled(fixedDelayString = "${reservas.reevaluacion.ms:3600000}", initialDelayString = "${reservas.reevaluacion.inicial.ms:60000}")
    public void ejecutar() {
        int n = service.reevaluarProximas(LocalDate.now(), LocalDate.now().plusDays(diasAdelante));
        log.info("Reevaluación de clima: {} reservas revisadas", n);
    }
}
