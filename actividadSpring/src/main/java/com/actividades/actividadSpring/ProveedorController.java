package com.actividades.actividadSpring;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/proveedores")
public class ProveedorController {

    private final ActividadService service;

    public ProveedorController(ActividadService service) {
        this.service = service;
    }

    @GetMapping
    public List<Proveedor> listar() { return service.listarProveedores(); }

    @GetMapping("/{id}")
    public Proveedor obtener(@PathVariable Long id) { return service.obtenerProveedor(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Proveedor crear(@Valid @RequestBody Proveedor p) { return service.crearProveedor(p); }
}
