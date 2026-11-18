# Órbita Académica — Proyecto completo

Proyecto Java 21 + Spring Boot 3.5 + Thymeleaf + Turso (HTTP Pipeline).

## Módulos
Dashboard, Estudiantes, Docentes, Carreras, Asignaturas, Matrículas, Períodos, Horarios, Calificaciones, Asistencia y Usuarios (solo ADMIN).

## Variables de entorno
- `TURSO_DATABASE_URL`: URL `libsql://...` o `https://...`
- `TURSO_AUTH_TOKEN`: token de Turso
- `ADMIN_USER`: usuario administrador inicial (por defecto `admin`)
- `ADMIN_PASSWORD`: clave administrador inicial (por defecto `admin123`; cambie esta variable en Render)
- `PORT`: asignado por Render

La aplicación convierte `libsql://` a `https://` para usar la API HTTP de Turso.

## Ejecutar local
En NetBeans: abrir carpeta del proyecto como Maven project y ejecutar `OrbitaAcademicaApplication`.
Terminal: `mvn spring-boot:run`

## Deploy Render
El repositorio incluye Dockerfile y render.yaml. Render necesita configurar TURSO_DATABASE_URL y TURSO_AUTH_TOKEN en Environment. No publique tokens en GitHub.

## Importante
Esta es una base funcional inicial. Los campos de relaciones (estudiante_id, carrera_id, asignatura_id, etc.) se introducen como ID numérico; la siguiente mejora recomendada es sustituirlos por desplegables que carguen los registros relacionados y validar reglas académicas.
