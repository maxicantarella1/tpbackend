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

---

## Paso 3 — API Gateway, Keycloak (OIDC/JWT) y zona de confianza

### Arquitectura de seguridad (qué decidimos)
```
Cliente --(Bearer JWT)--> API Gateway :8080 --(X-Internal-Secret + X-User-Name + X-User-Roles)--> Actividades :8081
                              |                                                              \--> Reservas :8082 --(X-Internal-Secret)--> Actividades
                              \-- valida JWT contra Keycloak :8180 (realm "turismo")
```
- **Keycloak** es el IdP (OIDC). Emite JWT con los roles de realm `ADMIN` y `USUARIO` en `realm_access.roles`. Realm importable: `keycloak/realm-turismo.json` (usuarios `admin/admin`, `ana/ana`, `pedro/pedro`).
- **Gateway = punto de entrada y Security Filter** (`SecurityConfig`, Spring Security WebFlux como Resource Server): autentica el JWT (firma, emisor, expiración) y autoriza **por ruta y método** según el rol. Todo lo que no está listado se **deniega** (`anyExchange().denyAll()`).
- **Zona de confianza = Gateway + microservicios + red interna.** Los servicios NO validan JWT; confían en el Gateway. Mecanismos que lo sostienen:
  1. En Docker solo se publica el puerto del Gateway (y Keycloak); los servicios no son alcanzables desde afuera (se configura en el paso de despliegue).
  2. **Secreto interno** (`X-Internal-Secret`): el `ConfianzaGatewayFilter` (en el JAR común) devuelve 403 a todo pedido que no lo traiga. Es una segunda barrera por si alguien llegara al puerto del servicio. Reservas lo reenvía al llamar a Actividades.
  3. El Gateway **borra** cualquier `X-User-*`/`X-Internal-Secret` que mande el cliente y los reescribe desde el JWT (`IdentidadFilter`): no se puede suplantar identidad.
- **Autorización en dos niveles:** el **Gateway** decide por *rol* (¿puede este rol llamar a esta ruta?); el **servicio** decide por *dato* (¿esta reserva es tuya?). Eso último no lo puede saber el Gateway.

### Tabla de permisos
| Operación | Anónimo | USUARIO | ADMIN |
|---|---|---|---|
| `GET /actividades/**` (catálogo, salidas) | sí | sí | sí |
| `POST/PUT/PATCH/DELETE /actividades/**`, `/proveedores/**` | no (401) | no (403) | sí |
| `POST /reservas` | no | sí (a su nombre) | sí (a nombre de otro) |
| `GET/PATCH /reservas/{id}`, `POST .../cancelar` | no | solo las suyas (403 si es ajena) | todas |
| `GET /reservas` | no | solo las suyas | todas |
| `POST /reservas/{id}/reevaluar` | no | no (403) | sí |
| `/actividades/*/salidas/*/cupo/**` (interno) | **denegado a todos** desde afuera | | |

### Por qué (justificaciones)
- **¿Por qué el Gateway valida el token y no cada servicio?** Un solo lugar para autenticación y reglas de rol (apunte 19): menos código repetido, un solo punto de cambio, menos superficie de exposición. Costo: los servicios dependen de que nadie los alcance sin pasar por el Gateway → por eso las barreras 1 y 2.
- **¿Por qué no JWT también en los servicios (defensa en profundidad)?** Es la alternativa más estricta (cada servicio como Resource Server + propagar el token o usar client credentials para las llamadas internas y el scheduler). La descartamos por complejidad y porque el scheduler de Reservas llamaría a Actividades sin usuario. Queda como mejora documentada.
- **¿Qué pasa si alguien llama directo al servicio?** 403 por el filtro de confianza (sin secreto). Si además tuviera el secreto, podría falsificar `X-User-Name`: por eso el secreto es de entorno (`GATEWAY_SECRET`), no va en el código y el puerto no se publica. Limitación reconocida: el secreto compartido no es tan fuerte como mTLS o JWT interno.
- **¿Por qué se bloquean los endpoints de cupo en el Gateway?** Son operaciones internas entre servicios: si se expusieran, un usuario podría "regalarse" cupo o vaciarlo. Solo Reservas debe llamarlos (red interna).
- **¿Por qué el catálogo es público?** Un turista debe poder ver actividades antes de registrarse; solo lectura.
- **¿Por qué `usuario` sale del token y no del body?** Si saliera del body, un USUARIO reservaría a nombre de otro. El body solo se respeta para ADMIN.
- **¿Por qué 403 y no 404 al pedir una reserva ajena?** Es una decisión; 404 ocultaría su existencia. Elegimos 403 por claridad; se puede cambiar.
- **¿CSRF deshabilitado?** Sí: la API es stateless, sin cookies ni sesión; la autenticación es un Bearer token que el navegador no adjunta solo.
- **¿Qué hace el rol en el JWT?** Keycloak lo pone en `realm_access.roles`; `SecurityConfig.rolesDeRealm` lo convierte a `ROLE_ADMIN`/`ROLE_USUARIO` para `hasRole(...)`.
- **¿Qué pasa si Keycloak está caído?** Con `jwk-set-uri` el Gateway valida los tokens vigentes con las claves ya cacheadas; no se pueden emitir tokens nuevos (login) hasta que vuelva. Los endpoints públicos siguen andando.
- **JAR común:** ahora aporta además `Seguridad` (nombres de cabeceras), `Identidad`, `ConfianzaGatewayFilter` y `AccesoDenegadoException` (403), usados por los dos servicios.

### Cómo probarlo (sin Docker completo)
```
docker compose -f docker-compose.keycloak.yml up -d          # Keycloak en :8180
cd common-lib && mvn clean install
cd ../actividadSpring && mvn spring-boot:run                  # :8081
cd ../ReservaSpring && mvn spring-boot:run                    # :8082
cd ../api-gateway && mvn spring-boot:run                      # :8080

TOKEN_ADMIN=$(curl -s -d client_id=turismo-client -d username=admin -d password=admin -d grant_type=password \
  http://localhost:8180/realms/turismo/protocol/openid-connect/token | jq -r .access_token)
TOKEN_ANA=$(curl -s -d client_id=turismo-client -d username=ana -d password=ana -d grant_type=password \
  http://localhost:8180/realms/turismo/protocol/openid-connect/token | jq -r .access_token)

# catálogo público
curl localhost:8080/actividades
# ADMIN crea proveedor, actividad y salida (USUARIO recibe 403)
curl -X POST localhost:8080/proveedores -H "Authorization: Bearer $TOKEN_ADMIN" -H 'Content-Type: application/json' -d '{"nombre":"Sierras Aventura","email":"a@b.com"}'
# ANA reserva (se guarda a su nombre) / sin token: 401 / ajena: 403
curl -X POST localhost:8080/reservas -H "Authorization: Bearer $TOKEN_ANA" -H 'Content-Type: application/json' \
  -d '{"actividadId":1,"fecha":"2026-12-01","cantidadPersonas":2,"edadMinimaParticipante":20,"edadMaximaParticipante":35,"nivelParticipante":"PRINCIPIANTE"}'
# acceso directo al servicio: 403
curl -i localhost:8081/actividades
```
Los ejemplos de `curl` directos a :8081/:8082 de pasos anteriores ahora dan 403 salvo que se envíe `-H 'X-Internal-Secret: dev-secret'` (para depurar un servicio aislado).

### Tests
- `ReservaServiceTest` (Mockito + `MockRestServiceServer`): reservar (confirmada, pendiente, clima inviable sin tocar cupo, suspendida, cupo agotado, compensación), reevaluar (a reprogramar, confirmar, suspendida, sin pronóstico, el scheduler no se detiene ante un error), modificar (cambio de fecha, solo diferencia, desde A_REPROGRAMAR, sin perder el lugar viejo), cancelar y propiedad de reservas.
- `SecurityConfigTest` (Gateway): 401 sin token, 403 por rol, endpoints internos bloqueados incluso para ADMIN, rutas desconocidas denegadas.
- Pendiente: probar de punta a punta con Keycloak real; verificar en jwt.io que el token trae `realm_access.roles`. **No se pudo compilar ni ejecutar nada en el entorno del asistente** (Maven Central bloqueado): correr `mvn test` en `ReservaSpring` y `api-gateway` antes de la defensa.

### Preguntas probables
- *Diferencia autenticación / autorización:* quién sos (JWT firmado por Keycloak) vs qué podés hacer (roles + dueño del recurso).
- *¿Qué contiene el JWT?* header, payload (iss, exp, preferred_username, realm_access.roles) y firma RS256; se valida con las claves públicas del JWKS de Keycloak.
- *¿Por qué RestClient y no Feign/RestTemplate?* Es el cliente recomendado en Spring 6.1+/Boot 3.2 (apunte 18).
- *¿Qué es el trusted zone en tu solución?* Gateway + servicios en la red interna; el Gateway es el único con acceso externo y el que traduce identidad en cabeceras que los servicios aceptan solo con el secreto.
