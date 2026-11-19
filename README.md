# Órbita Académica – reinicio controlado de base de datos

Proyecto Java 21, Spring Boot 3.5, Thymeleaf y Turso/libSQL.

## IMPORTANTE: qué conserva el SQL

El script `database/orbita_academica_reinicio_preservando_usuarios_estudiantes_docentes.sql` reinicia las tablas académicas/operativas y conserva sin eliminarlas:

- `Usuarios`
- `Estudiantes`
- `Docentes`
- `Carreras` (también se conserva para no romper las referencias de estudiantes a su carrera).

El script elimina las tablas académicas/operativas anteriores y las vuelve a crear vacías: períodos, asignaturas, secciones, horarios, matrículas, calificaciones, asistencia, cuotas, pagos, documentos, tickets, notificaciones, evaluaciones, ausencias, lista de espera, bitácora y eventos. Por eso se pierden los registros que existan en esas tablas. Los registros de las cuatro tablas protegidas no se eliminan.

## Cómo ejecutarlo en Turso

1. En Turso, abre la base de datos correcta (`orbitaacademica`).
2. Antes de continuar, crea un respaldo de la base.
3. Abre **Edit Data** / SQL editor.
4. Abre el archivo `database/orbita_academica_reinicio_preservando_usuarios_estudiantes_docentes.sql`.
5. Copia y pega TODO el contenido y ejecútalo una sola vez.
6. Al final, el script devuelve los conteos de `Usuarios`, `Estudiantes`, `Docentes` y `Carreras`. Comprueba que se mantengan los registros esperados.
7. Reinicia o vuelve a desplegar la aplicación en Render.

No ejecutes scripts antiguos de migración v3/v4 después de este reinicio. Este paquete contiene un único SQL canónico para evitar confusiones.

## Variables de entorno en Render

- `TURSO_DATABASE_URL`: URL de Turso que comienza con `libsql://`.
- `TURSO_AUTH_TOKEN`: token de acceso de Turso.

## Compilar localmente

Requiere JDK 21 y Maven:

```bash
mvn clean package
mvn spring-boot:run
```

## Cambios incluidos en el código

- Conversión de tipos numéricos devueltos por Turso a `Long` / `Double` cuando corresponde, evitando expresiones numéricas de Thymeleaf con cadenas.
- La oferta estudiantil convierte explícitamente `ya_matriculado`, `inscritos` y `cupo` a números antes de renderizar.
- La creación de horarios utiliza `HorariosSeccion`, incluyendo la columna `aula`.
- Se mantienen los portales y controladores existentes del proyecto de origen.

## Nota de seguridad

El SQL no puede ejecutarse remotamente desde este paquete: debes pegarlo y ejecutarlo en tu propia base Turso. Guarda el token de Turso únicamente en variables de entorno de Render; no lo incluyas en el repositorio ni en el ZIP.

La compilación Maven debe ejecutarse localmente o en Render para confirmar el despliegue con tus variables de entorno.
