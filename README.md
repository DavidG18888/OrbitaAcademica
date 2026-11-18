# Órbita Académica — Gestión Integral

Proyecto inicial Java 21 + Spring Boot + Thymeleaf, con base de datos Turso (LibSQL) vía HTTP API.

## Requisitos
- Java 21
- Maven (NetBeans puede usar Maven integrado)
- Cuenta Turso y una base de datos creada
- Cuenta Render y GitHub

## Variables de entorno
- `TURSO_DATABASE_URL`: URL de la base Turso, normalmente `https://NOMBRE-DB-NOMBRE-USUARIO.turso.io`
- `TURSO_AUTH_TOKEN`: token de acceso de Turso
- `ADMIN_USER`: usuario inicial (opcional, default `admin`)
- `ADMIN_PASSWORD`: contraseña inicial (opcional, default `OrbitaAdmin2026!`; cambiarla en Render antes de usar)
- `PORT`: Render lo configura automáticamente

## Inicio local
Configurar las variables de entorno en el sistema/IDE y ejecutar:
`mvn spring-boot:run`

Abrir `http://localhost:8080`.

## Render
- Seleccionar despliegue desde Dockerfile (incluido en el proyecto).
- Añadir las variables `TURSO_DATABASE_URL`, `TURSO_AUTH_TOKEN`, `ADMIN_USER` y `ADMIN_PASSWORD` en Environment.

En el primer inicio la aplicación crea las tablas iniciales y el administrador si no existe.

## Acceso inicial
Usuario: `admin`
Contraseña: `OrbitaAdmin2026!`

**Cambiar ADMIN_PASSWORD en Render antes de poner el sistema en uso.**

Esta entrega es la base inicial: login, sesión, panel y creación de tablas. Los módulos de estudiantes, docentes, matrícula, notas, asistencia y JasperReports se desarrollarán en etapas posteriores.


## Módulos añadidos

Se añadieron pantallas CRUD generales para estudiantes, docentes, carreras, asignaturas, períodos, matrículas, horarios, calificaciones, asistencia y usuarios (usuarios solo ADMIN). Las tablas nuevas se crean automáticamente en el arranque por `InicioService`; también está actualizado `database/orbita_academica.sql`.

**Nota:** los campos de relación (ID de estudiante, asignatura, carrera, docente y período) se introducen por ID en esta primera versión; todavía no son listas desplegables relacionadas. Revise los datos antes de guardar.
