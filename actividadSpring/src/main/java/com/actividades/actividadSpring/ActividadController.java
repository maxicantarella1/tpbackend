package com.actividades.actividadSpring;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/actividades")
public class ActividadController {

    private final ActividadService service;

    public ActividadController(ActividadService service) {
        this.service = service;
    }

    @GetMapping
    public List<Actividad> listar() { return service.listar(); }

    @GetMapping("/{id}")
    public Actividad obtener(@PathVariable Long id) { return service.obtener(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Actividad crear(@Valid @RequestBody Actividad a) { return service.crear(a); }

    @PutMapping("/{id}")
    public Actividad actualizar(@PathVariable Long id, @Valid @RequestBody Actividad a) {
        return service.actualizar(id, a);
    }

    @PatchMapping("/{id}/estado")
    public Actividad cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest req) {
        return service.cambiarEstado(id, req.estado());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable Long id) { service.borrar(id); }

    // ---- salidas (actividad en una fecha, con su propio cupo) ----

    @PostMapping("/{id}/salidas")
    @ResponseStatus(HttpStatus.CREATED)
    public Salida crearSalida(@PathVariable Long id, @Valid @RequestBody SalidaRequest req) {
        return service.crearSalida(id, req);
    }

    @GetMapping("/{id}/salidas")
    public List<Salida> listarSalidas(@PathVariable Long id) { return service.listarSalidas(id); }

    @GetMapping("/{id}/salidas/{fecha}")
    public Salida obtenerSalida(@PathVariable Long id,
                                @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return service.obtenerSalida(id, fecha);
    }

    @PatchMapping("/{id}/salidas/{fecha}/cupo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descontarCupo(@PathVariable Long id,
                              @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                              @Valid @RequestBody CupoRequest req) {
        service.descontarCupo(id, fecha, req.cantidad());
    }

    @PostMapping("/{id}/salidas/{fecha}/cupo/devolucion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void devolverCupo(@PathVariable Long id,
                             @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                             @Valid @RequestBody CupoRequest req) {
        service.devolverCupo(id, fecha, req.cantidad());
    }
}
