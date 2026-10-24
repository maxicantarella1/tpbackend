package com.actividades.actividadSpring;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable Long id) { service.borrar(id); }

    @PatchMapping("/{id}/cupo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descontarCupo(@PathVariable Long id, @Valid @RequestBody CupoRequest req) {
        service.descontarCupo(id, req.cantidad());
    }

    @PostMapping("/{id}/cupo/devolucion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void devolverCupo(@PathVariable Long id, @Valid @RequestBody CupoRequest req) {
        service.devolverCupo(id, req.cantidad());
    }
}
