package com.actividades.actividadSpring;

import com.actividades.actividadSpring.ActividadRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/actividades")
public class ActividadController {

    private final ActividadRepository repo;

    public ActividadController(ActividadRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Actividad> listar() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public Actividad obtener(@PathVariable Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ActividadNoEncontradaException(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)          // 201
    public Actividad crear(@Valid @RequestBody Actividad a) {
        return repo.save(a);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)       // 204
    public void borrar(@PathVariable Long id) {
        repo.deleteById(id);
    }
}