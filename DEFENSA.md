# Guía de defensa oral — TPI Backend 2026 (Dominio 5)

## Paso 1 — Base corregida

### Qué se cambió y por qué
| Cambio | Motivo |
|---|---|
| `PATCH /actividades/{id}/cupo` en Actividades | Reservas ya lo llamaba pero no existía: toda reserva fallaba. |
| Descuento de cupo con `UPDATE ... WHERE cupo >= :n` | Operación atómica en la BD: dos reservas simultáneas no pueden consumir el mismo cupo (sin race condition). |
| `CupoInsuficienteException` extiende `ConflictoNegocioException` | Ahora devuelve 409 en vez de 500. |
| `POST /actividades/{id}/cupo/devolucion` + compensación en Reservas | Si falla el guardado de la reserva tras descontar el cupo, se devuelve el cupo (patrón de compensación, "saga" simple). |
| Handlers duplicados eliminados; ambos servicios importan `ManejadorErroresComun` | Para eso existe el JAR común: un único formato de error (`ErrorResponse`) en todo el sistema. |
| `common-lib` compilado contra Spring Boot 3.2.5 | Estaba en 2.5.6 mientras los servicios usan 3.2.x. |

### Preguntas probables
- **¿Para qué sirve el JAR común?** Contrato de errores y excepciones de negocio compartidas. Se instala en `.m2` (`mvn install`) y los servicios lo consumen como dependencia Maven. Evita copiar y pegar y mantiene respuestas de error homogéneas.
- **¿Por qué el UPDATE condicional y no leer-modificar-guardar?** Leer y después escribir permite que dos requests lean el mismo cupo. El UPDATE condicional es atómico. La alternativa es lock optimista (`@Version`) con reintentos; el UPDATE es más simple.
- **¿Qué pasa si Actividades cae en medio de la reserva?** Timeouts de 2 s/3 s → `ServicioNoDisponibleException` → 503. Si cae después de descontar y falla el guardado, la devolución es "best effort": se registra el error para conciliar. Limitación conocida: no hay reintento ni cola.
- **¿Por qué 409 y no 400/422?** El pedido es válido pero choca con el estado actual del recurso (sin cupo).
- **¿Por qué Reservas guarda solo `actividadId`?** Cada servicio es dueño de sus datos; no hay claves foráneas entre servicios.

### Cómo compilar y probar
```
cd common-lib && mvn clean install
cd ../actividadSpring && mvn spring-boot:run   # :8081
cd ../ReservaSpring && mvn spring-boot:run     # :8082
curl -X POST localhost:8081/actividades -H 'Content-Type: application/json' \
  -d '{"nombre":"Trekking","latitud":-31.4,"longitud":-64.2,"cupoDisponible":5,"precio":100,"maxProbLluvia":40,"maxVientoKmh":30}'
curl -X POST localhost:8082/reservas -H 'Content-Type: application/json' \
  -d '{"actividadId":1,"usuario":"ana","fecha":"2026-12-01","cantidadPersonas":2}'
```
Nota: estos cambios no se pudieron compilar en el entorno de trabajo (Maven Central bloqueado); compilar localmente antes de seguir.
