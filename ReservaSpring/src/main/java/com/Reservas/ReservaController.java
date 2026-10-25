package com.Reservas;

import com.turismo.common.Identidad;
import com.turismo.common.Seguridad;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * La identidad llega en cabeceras que SOLO el Gateway completa (y el filtro de confianza garantiza que
 * el pedido vino del Gateway). El control de rol por ruta se hace en el Gateway; acá se aplica lo que
 * depende del dato: que un USUARIO solo toque sus reservas.
 */
@RestController
@RequestMapping("/reservas")
public class ReservaController {

    private final ReservaService service;

    public ReservaController(ReservaService service) {
        this.service = service;
    }

    private static Identidad quien(String usuario, String roles) {
        return Identidad.desde(usuario, roles);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Reserva crear(@Valid @RequestBody ReservaRequest req,
                         @RequestHeader(value = Seguridad.HEADER_USER, required = false) String usuario,
                         @RequestHeader(value = Seguridad.HEADER_ROLES, required = false) String roles) {
        return service.reservar(req, quien(usuario, roles));
    }

    @PatchMapping("/{id}")
    public Reserva modificar(@PathVariable Long id, @Valid @RequestBody ModificarReservaRequest req,
                             @RequestHeader(value = Seguridad.HEADER_USER, required = false) String usuario,
                             @RequestHeader(value = Seguridad.HEADER_ROLES, required = false) String roles) {
        return service.modificar(id, req, quien(usuario, roles));
    }

    @PostMapping("/{id}/cancelar")
    public Reserva cancelar(@PathVariable Long id,
                            @RequestHeader(value = Seguridad.HEADER_USER, required = false) String usuario,
                            @RequestHeader(value = Seguridad.HEADER_ROLES, required = false) String roles) {
        return service.cancelar(id, quien(usuario, roles));
    }

    @PostMapping("/{id}/reevaluar")   // el Gateway la restringe a ADMIN
    public Reserva reevaluar(@PathVariable Long id) {
        return service.reevaluar(id);
    }

    @GetMapping
    public List<Reserva> listar(@RequestHeader(value = Seguridad.HEADER_USER, required = false) String usuario,
                                @RequestHeader(value = Seguridad.HEADER_ROLES, required = false) String roles) {
        return service.listar(quien(usuario, roles));
    }

    @GetMapping("/{id}")
    public Reserva obtener(@PathVariable Long id,
                           @RequestHeader(value = Seguridad.HEADER_USER, required = false) String usuario,
                           @RequestHeader(value = Seguridad.HEADER_ROLES, required = false) String roles) {
        return service.obtenerPropia(id, quien(usuario, roles));
    }
}
