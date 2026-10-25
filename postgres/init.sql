-- Una instancia de PostgreSQL, UNA BASE POR MICROSERVICIO (cada servicio es dueño exclusivo de sus datos).
-- Credenciales de DESARROLLO: en un entorno real se inyectan como secretos.
CREATE USER actividades WITH PASSWORD 'actividades';
CREATE DATABASE actividades_db OWNER actividades;

CREATE USER reservas WITH PASSWORD 'reservas';
CREATE DATABASE reservas_db OWNER reservas;

CREATE USER keycloak WITH PASSWORD 'keycloak';
CREATE DATABASE keycloak OWNER keycloak;
