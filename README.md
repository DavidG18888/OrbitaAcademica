# Órbita Académica — Gestión Integral

Java 21 + Spring Boot 3.5 + Thymeleaf, base de datos Turso (LibSQL) vía HTTP API.

## Variables de entorno
`TURSO_DATABASE_URL`, `TURSO_AUTH_TOKEN`, `ADMIN_USER` (default `admin`), `ADMIN_PASSWORD` (cambiarla), `PORT`.

## Ejecutar
`mvn spring-boot:run` y abrir `http://localhost:8080`. Con Docker/Render se usa el `Dockerfile` incluido.
Al iniciar se crean las tablas y el administrador (y se agregan columnas nuevas a bases ya existentes).

## Administración (`/admin/...`)
Estudiantes, docentes, carreras, asignaturas (con conteo de secciones), períodos, secciones con horario (día, horas, aula, cupo)
y matrículas (valida duplicados y cupo, permite retirar/reactivar y filtrar por período).
Al crear un estudiante o docente se crea su usuario: usuario = código, clave inicial = código.

## Portal docente (`/docente`)
Inicio con resumen y clases de hoy · Mis secciones · Horario semanal · Lista de estudiantes y registro de 3 parciales ·
Control de asistencia por fecha · Reporte de notas (estadísticas, distribución, CSV, imprimir) · Mi cuenta.

## Portal estudiantil (`/estudiante`)
Inicio (promedio, asistencia, UV, clases de hoy) · Mis asignaturas · Calificaciones · Horario semanal · Asistencia ·
Historial académico por período (imprimir/PDF) · Descargar notas (CSV) · Datos personales y cambio de contraseña.

Nota mínima aprobatoria: 65 (constante `Util.PASA`). Los accesos por rol se validan en `config/WebConfig.java`.
