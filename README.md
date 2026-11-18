# Órbita Académica
Sistema académico base con Spring Boot 3, Java 21, Thymeleaf y Turso (HTTP Pipeline).

## Variables de entorno
- `TURSO_DATABASE_URL`: URL de Turso (puede ser `libsql://...` o `https://...`)
- `TURSO_AUTH_TOKEN`: token de acceso
- `ADMIN_USER`: usuario inicial (default `admin`)
- `ADMIN_PASSWORD`: contraseña inicial (default insegura para desarrollo; cambiar en Render)

## Ejecutar
`mvn spring-boot:run`

## Despliegue
Render: Runtime Docker o Java; puerto se toma de `PORT`. No subir secretos al repositorio.

Este ZIP contiene un sistema funcional inicial: login, paneles por rol, CRUD básico para usuarios, estudiantes, docentes, asignaturas, períodos, matrículas, calificaciones y asistencia. Algunas funciones de reportes están preparadas como vistas/exportaciones simples y no reemplazan aún reportes PDF con JasperReports.
