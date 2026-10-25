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

### Cómo compilar y probar (actualizado en el paso 2b)
```
cd common-lib && mvn clean install
cd ../actividadSpring && mvn spring-boot:run   # :8081
cd ../ReservaSpring && mvn spring-boot:run     # :8082
curl -X POST localhost:8081/proveedores -H 'Content-Type: application/json' -d '{"nombre":"Sierras Aventura","email":"a@b.com"}'
curl -X POST localhost:8081/actividades -H 'Content-Type: application/json' -d '{"nombre":"Trekking","proveedorId":1,"latitud":-31.4,"longitud":-64.2,"capacidadPorSalida":5,"precio":100,"maxProbLluvia":40,"maxVientoKmh":30,"edadMinima":12,"nivelRequerido":"PRINCIPIANTE","mesInicio":1,"mesFin":12}'
curl -X POST localhost:8081/actividades/1/salidas -H 'Content-Type: application/json' -d '{"fecha":"2026-12-01"}'
curl -X POST localhost:8082/reservas -H 'Content-Type: application/json' -d '{"actividadId":1,"usuario":"ana","fecha":"2026-12-01","cantidadPersonas":2,"edadMinimaParticipante":20,"edadMaximaParticipante":35,"nivelParticipante":"PRINCIPIANTE"}'
```
Nota: estos cambios no se pudieron compilar en el entorno de trabajo (Maven Central bloqueado); compilar localmente antes de seguir.

---

## Paso 2 — Reglas del dominio 5 y API externa de clima

### Reglas implementadas (en `ReservaService`, antes de tocar el cupo)
1. **Temporada:** cada actividad tiene `mesInicio`/`mesFin` (puede cruzar el año, ej. nov–mar). Fuera de temporada → **422**.
2. **Participantes:** `edadMinima` de la actividad vs. `edadMinimaParticipante` del pedido → **422**.
3. **Clima (solo si `aireLibre`):** si prob. de lluvia > `maxProbLluvia` o viento > `maxVientoKmh` → **422** "inviable ese día".
4. **Cupo:** descuento atómico en Actividades → **409** si no alcanza.
5. **Ciclo de vida:** `PENDIENTE` → `CONFIRMADA` / `CANCELADA`.
   - `POST /reservas/{id}/reevaluar`: vuelve a consultar el clima. Si ahora es viable pasa a CONFIRMADA; si se volvió inviable pasa a CANCELADA **y devuelve el cupo** (el caso "reserva válida que se vuelve inviable" del anexo).
   - `POST /reservas/{id}/cancelar`: cancela y devuelve cupo; 409 si ya estaba cancelada.

### API externa: Open-Meteo (https://open-meteo.com, pública, sin API key)
- **Por qué es necesaria:** el anexo dice que condiciones externas deciden si una actividad puede realizarse. El pronóstico (lluvia/viento) no es un dato que podamos tener nosotros; sin él la regla 3 no existe. No es "decorativa" (a diferencia de convertir moneda).
- **Qué proceso depende:** confirmación de reserva y reevaluación.
- **Qué datos usa:** `precipitation_probability_max` y `wind_speed_10m_max` del día de la reserva para la lat/long de la actividad.
- **Cómo se integra:** `ClimaClient` con `RestClient` propio (bean `climaRestClient`), timeouts 2 s / 3 s, URL en `application.properties` (`clima.url`).
- **Si falla o responde algo inesperado:** `ClimaClient` devuelve `Optional.empty()` (nunca lanza ni inventa datos). Al reservar → la reserva queda **PENDIENTE** (cupo reservado, sin confirmar) con `motivo`; al reevaluar → **503**. Decisión: no confirmar a ciegas ni rechazar por una caída ajena.
- **Pronóstico solo hasta ~16 días:** para fechas más lejanas la API responde error → mismo camino (PENDIENTE) y se reevalúa cuando se acerque la fecha.
- **Por qué en Reservas y no en Actividades:** la regla "¿puede realizarse esta reserva en esta fecha?" es del proceso de reserva; Actividades solo expone los umbrales (`maxProbLluvia`, `maxVientoKmh`) y las coordenadas.

### Preguntas probables
- **¿Por qué 422 y no 400?** 400 = request mal formado (validación `@Valid`). 422 = bien formado pero viola una regla del negocio. 409 = choca con el estado actual (cupo).
- **¿Por qué validar antes de descontar el cupo?** Para no tener que compensar en los casos más comunes de rechazo.
- **¿Qué pasa si el clima cambia después de confirmar?** Se llama a `reevaluar`; hoy lo dispara un cliente/admin. Mejora posible: tarea programada (`@Scheduled`) que reevalúe reservas próximas.
- **¿Circuit breaker?** Hoy hay timeouts + fallback (Optional vacío → PENDIENTE). Resilience4j sería el siguiente paso; la lógica de fallback ya está aislada en `ClimaClient`.
- **¿Cómo lo testeaste?** `ClimaClientTest` usa `MockRestServiceServer` (respuesta OK, 500, JSON inesperado) y prueba las reglas de temporada/clima sin red.
- **Limitación honesta:** no se pudo compilar ni correr los tests en el entorno de desarrollo del asistente (Maven Central bloqueado). Correr `mvn test` en Reservas antes de la defensa.
- **Datos de prueba nuevos de Actividad:** `aireLibre`, `edadMinima`, `mesInicio`, `mesFin` (ver ejemplo de POST actualizado: agregar esos campos).

---

## Paso 2b — Dominio completo (proveedores, salidas por fecha, modificar, scheduler)

### Qué se agregó y por qué
| Cambio | Motivo / cómo se defiende |
|---|---|
| **Salida** (actividad + fecha + cupo) | Antes el cupo era global por actividad: reservar el 1/12 le quitaba lugares al 2/12. El cupo es de la *salida*. Es el modelo real de una excursión (una oferta por fecha). Restricción única (actividad, fecha). |
| **Proveedor** + `proveedorId` en Actividad | El anexo: actividades "ofrecidas por distintos proveedores". No se usa FK a otro servicio; es el mismo servicio, y se valida que exista y esté activo. |
| **Estado de actividad** (ACTIVA/SUSPENDIDA) + `PATCH /actividades/{id}/estado` | El proveedor puede suspender. Reservar en una suspendida → 422; las reservas existentes pasan a A_REPROGRAMAR al reevaluar. |
| `PUT /actividades/{id}`, borrar con 409 si tiene salidas | Edición completa; no se borra lo que tiene historial (se suspende). |
| **Participantes:** edad mínima y máxima, `nivelRequerido` vs `nivelParticipante` (enum `Nivel` en common-lib) | Característica de los participantes pedida por el anexo. El enum compartido justifica el JAR común. |
| **Estado A_REPROGRAMAR** | Reserva válida que se vuelve inviable: libera el cupo pero NO se pierde la reserva; el usuario elige nueva fecha (`PATCH /reservas/{id}`) o cancela. Mejor que cancelar solo. |
| **Modificar reserva** (`PATCH /reservas/{id}`: fecha y/o cantidad) | Revalida todas las reglas con los nuevos valores. Primero toma el cupo nuevo y después libera el viejo (nunca se pierde el lugar); en la misma fecha ajusta solo la diferencia. |
| **Tarea programada** (`ReevaluadorClima`, `@Scheduled`) | Cada hora (configurable) reevalúa reservas PENDIENTE/CONFIRMADA de los próximos 7 días; un error en una no frena a las demás. |
| **Precio total** = precio × personas | Se calcula al reservar y al modificar. |

### Preguntas probables
- **¿Por qué la salida es una entidad y no un campo?** Porque el cupo depende de la fecha; con una tabla de salidas el descuento atómico es por (actividad, fecha).
- **¿Quién crea las salidas?** El proveedor/admin con `POST /actividades/{id}/salidas`. Reservar una fecha sin salida → 422.
- **¿Qué pasa si dos usuarios reservan el último lugar?** El `UPDATE ... WHERE cupo >= n` es atómico: uno gana (204) y el otro recibe 409.
- **¿Por qué A_REPROGRAMAR y no CANCELADA?** El usuario no tiene la culpa; se conserva la reserva para reprogramar. Libera el cupo para no bloquear una fecha inviable.
- **¿Qué pasa si falla la modificación a mitad de camino?** Se compensa devolviendo el cupo recién tomado. Limitación: la devolución es best-effort (se loguea); una saga/outbox sería el siguiente nivel.
- **¿Por qué el scheduler está en Reservas?** Es el dueño del ciclo de vida de la reserva; consulta a Actividades y al clima. Con varias instancias habría que evitar ejecuciones duplicadas (ShedLock); hoy es una sola instancia.
- **Limitación honesta:** `modificar` en la misma fecha con aumento de cantidad toma solo la diferencia; si esa llamada tiene éxito y luego falla el guardado, se devuelve esa diferencia.
- Tests: `ClimaClientTest` cubre reglas de participantes, temporada, suspendida, clima y estados que retienen cupo. Faltan tests de servicio con `MockRestServiceServer` sobre `modificar`/`reevaluar` (pendiente).
