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

Nota mínima aprobatoria: 70 (constante `Util.PASA`). Los parciales se registran como puntos obtenidos: Parcial I máximo 25, Parcial II máximo 35 y Parcial III máximo 40. La nota final suma los tres parciales y los puntos extra, con un máximo de 100.

## Actualización de base de datos v2
- `database/orbita_academica_v2_migracion.sql`: agrega las tablas para horarios flexibles, documentos, cuotas, comprobantes, notificaciones, tickets, evaluación docente y revisión de ausencias. Conserva los registros existentes. Los `ALTER TABLE` se ejecutan una sola vez; omite los que ya existan.
- `database/orbita_academica_v2_recrear_desde_cero.sql`: elimina todas las tablas y sus datos. Es destructivo y solo debe utilizarse para reiniciar un entorno de pruebas.
- La aplicación también crea las nuevas tablas al iniciar y agrega los campos de calificaciones/secciones que faltan.

## Alcance de esta actualización
Se retiró el módulo Diagnóstico del menú y del controlador, se reorganizó el menú administrativo y se actualizó el diseño visual general. Se ajustó el cálculo de notas a 25/35/40, aprobación desde 70 y puntos extra con tope de 100. Se incorporó el esquema de base de datos para los módulos solicitados.

**Importante:** el esquema de datos es la base para los módulos de matrícula autónoma, carga/revisión de documentos, cuotas y comprobantes, tickets, evaluaciones, fotografía de perfil, horarios por múltiples bloques y flujo administrativo de ausencias. Esos flujos todavía requieren sus pantallas, controladores, validaciones de permisos y almacenamiento privado de archivos; no se presentan como terminados en esta versión. Los accesos por rol se validan en `config/WebConfig.java`.
