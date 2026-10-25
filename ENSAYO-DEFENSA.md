# Ensayo de la defensa oral

Reglas del juego: cualquier integrante puede ser interrogado sobre **cualquier** parte y puede pedirse un cambio en vivo. Responder siempre con esta estructura: **qué hace → por qué así → qué alternativa descartamos → qué limitación tiene**.

## 1. Guion de demo (≈10 min)
1. `docker compose up --build`; mostrar `docker compose ps` (5 contenedores) y decir que solo se publican gateway y Keycloak.
2. Swagger UI en `localhost:8080/swagger-ui.html`: las dos APIs documentadas.
3. Sin token: `GET /reservas` → 401. Catálogo público: `GET /actividades` → 200.
4. Token de **admin**: crear proveedor → actividad (aire libre, edad mínima, temporada) → salida con fecha.
5. Token de **ana**: intentar crear actividad → **403**. Reservar → 201 CONFIRMADA (o PENDIENTE si no hay pronóstico).
6. Reservar de nuevo hasta agotar cupo → **409**. Reservar fuera de temporada o con edad menor → **422**.
7. Token de **pedro** pidiendo la reserva de ana → **403** (dueño del recurso).
8. Admin: `POST /reservas/{id}/reevaluar` (si el clima es inviable → A_REPROGRAMAR y el cupo se libera); ana modifica fecha con `PATCH`.
9. Intento directo al servicio (`localhost:8081`) → no conecta; con el puerto abierto, 403 por falta de secreto.
10. `docker compose stop actividades` → reservar → **503** rápido (circuit breaker tras unos fallos).

## 2. Preguntas y respuestas modelo

**Dominio y servicios**
- *¿Por qué dos servicios?* Actividades es el catálogo y el cupo (datos del proveedor); Reservas es el proceso del usuario. Cambian por motivos distintos y se escalan por separado. Un solo servicio mezclaría ambas responsabilidades.
- *¿Qué proceso atraviesa los dos?* Reservar: Reservas valida con la actividad, consulta el clima, Actividades descuenta el cupo de la salida y Reservas guarda. Si falla el guardado, compensa devolviendo el cupo.
- *¿Por qué el cupo está en la salida y no en la actividad?* El cupo depende de la fecha; si fuera de la actividad, reservar el 1/12 consumiría lugares del 2/12.
- *¿Qué pasa con una reserva que se vuelve inviable?* Pasa a A_REPROGRAMAR, libera el cupo y el usuario elige nueva fecha o cancela. No se cancela sola porque el usuario no tiene la culpa.
- *¿Cómo se evita sobreventa con dos usuarios a la vez?* `UPDATE ... WHERE cupo >= n` atómico en la base; uno gana y el otro recibe 409.

**API externa**
- *¿Por qué Open-Meteo es necesaria?* El clima decide si una actividad al aire libre puede realizarse; no es un dato que podamos guardar como configuración.
- *¿Qué pasa si cae o responde mal?* Respuesta vacía → reserva PENDIENTE (no se confirma a ciegas); tras varios fallos abre el circuit breaker; la tarea programada reevalúa luego.

**Seguridad**
- *Autenticación vs autorización.* Quién sos (JWT firmado por Keycloak) vs qué podés hacer (rol + si el recurso es tuyo).
- *¿Qué hace el Gateway y qué el servicio?* Gateway: valida el JWT y decide por rol y ruta. Servicio: decide por dato (reserva propia).
- *¿Qué es tu zona de confianza?* Red interna Docker: gateway + servicios + base. Solo el gateway está expuesto; además los servicios exigen un secreto interno y el gateway borra y reescribe las cabeceras de identidad.
- *¿Y si alguien falsifica `X-User-Name`?* Solo podría hacerlo con acceso a la red interna y al secreto. Límite reconocido; la mejora sería JWT también en los servicios o mTLS.
- *¿Por qué CSRF deshabilitado?* API stateless con Bearer token, sin cookies de sesión.
- *¿Cómo llegan los roles a Spring?* Keycloak los pone en `realm_access.roles`; un converter los transforma en `ROLE_*`.

**Persistencia y despliegue**
- *¿Una base por servicio o compartida?* Una instancia, una base por servicio: autonomía de datos sin duplicar infraestructura. Reservas guarda `actividadId` sin FK.
- *¿Qué limitación tiene `ddl-auto=update`?* No es apto para producción; corresponde Flyway/Liquibase.
- *¿Por qué `common-lib` y qué NO compartimos?* Compartimos lo transversal y estable: errores, identidad, nivel, filtro de confianza. NO compartimos entidades ni reglas de negocio, para no acoplar los servicios.
- *¿Por qué se compila `common-lib` dentro del Dockerfile?* Para builds reproducibles sin depender del `.m2` de una máquina.

**Errores y robustez**
- *¿Cuándo 400, 404, 409, 422, 503?* 400 datos mal formados; 404 no existe; 409 conflicto de estado (sin cupo, ya cancelada); 422 regla de negocio (temporada, edad, clima); 503 dependencia caída.
- *¿Qué es el circuit breaker y qué no cuenta como fallo?* Corta llamadas a un servicio que falla; los 4xx no cuentan porque son respuestas de negocio de un servicio sano.
- *¿Qué pasa si falla la devolución de cupo en una compensación?* Se registra en el log para conciliar manualmente; limitación conocida (haría falta una cola/outbox).

## 3. Cambios en vivo probables (dónde tocar)
| Pedido | Dónde |
|---|---|
| Agregar un campo a Actividad (p. ej. `duracionHoras`) | `Actividad.java` (+ `ActividadDto` si Reservas lo usa) |
| Nueva regla: máximo de personas por reserva | `ReservaService.validarReglas` (lanzar `ReglaNegocioException`) |
| Cambiar umbral de lluvia o la forma de decidir inviable | `ReservaService.problemaClimatico` |
| Nuevo rol (p. ej. PROVEEDOR) | `keycloak/realm-turismo.json` + `SecurityConfig` (matchers) |
| Cambiar qué ruta es pública | `SecurityConfig.pathMatchers` |
| Nuevo código de error | excepción en `common-lib` + handler en `ManejadorErroresComun` (recordar `mvn install` de common-lib) |
| Cambiar timeout o parámetros del circuit breaker | `ClientesConfig` / `application.properties` (`resiliencia.*`) |
| Agregar un endpoint | Controller → Service → (Repository); anotar con `@Operation` |
| Cambiar la frecuencia de reevaluación | `reservas.reevaluacion.ms` en `application.properties` |

## 4. Honestidad ante el tribunal
- Lo que **sí** podemos afirmar: reglas de dominio, flujo entre servicios, seguridad por capas, errores coherentes, despliegue con Compose, tests de servicio y de gateway.
- Lo que hay que decir como **limitación**: secreto compartido en vez de JWT interno, `ddl-auto=update`, compensación best-effort sin outbox, estado del circuito por instancia, reevaluación sin distribución entre réplicas.
- Si no sabés algo: explicá el criterio, no inventes. El enunciado acepta uso de IA pero exige que puedas explicar y modificar cada parte.
