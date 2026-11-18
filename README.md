# Órbita Académica — Gestión Integral

Java 21 + Spring Boot 3.5 + Thymeleaf, base de datos Turso (LibSQL) vía HTTP API.

## Variables de entorno
`TURSO_DATABASE_URL`, `TURSO_AUTH_TOKEN`, `ADMIN_USER` (default `admin`), `ADMIN_PASSWORD` (cambiarla), `PORT`.

## Ejecutar
`mvn spring-boot:run` y abrir `http://localhost:8080`. Con Docker/Render se usa el `Dockerfile` incluido.
Al iniciar se crean las tablas y el administrador (también se agregan las columnas nuevas a bases ya existentes).

## Módulos
- **Administración** (`/admin/...`): estudiantes, docentes, carreras, asignaturas, períodos, secciones y horarios, matrículas.
  Al crear un estudiante o docente se crea su usuario: usuario = código, clave inicial = código.
- **Docente** (`/docente`): sus secciones, lista de estudiantes y registro de 3 parciales.
- **Estudiante** (`/estudiante`): sus asignaturas, horario, notas, promedio y estado (aprobada desde 65).

Los accesos por rol se validan en `config/WebConfig.java`.
