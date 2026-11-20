# Órbita Académica – entrega integrada

Aplicación basada en Java 21, Spring Boot 3.5, Thymeleaf y Turso/libSQL.

## Módulos incluidos

- Acceso y portales por rol (administración, docente y estudiante).
- Gestión académica existente: carreras, asignaturas, períodos, secciones, horarios, matrículas, asistencia y notas.
- Horarios de sección por bloques recurrentes y fechas específicas.
- Oferta académica y matrícula estudiantil, con validación de cupos, duplicados, prerrequisitos y cruces conforme a las validaciones del módulo existente.
- Calificaciones 25/35/40, extras no negativos, límite final de 100 y cierre docente.
- Historial académico, constancia y reportes impresos mediante JasperReports. No se ofrecen descargas Excel ni CSV; los reportes se generan con JasperReports y se abren en línea para impresión.
- Cuotas mensuales, notificación a estudiantes y revisión de comprobantes.
- Expediente digital (título, partida, DNI) y revisión administrativa.
- Fotografía de perfil.
- Tickets, respuestas y seguimiento.
- Evaluación docente y reporte agregado por sección.
- Reporte docente de ausencia, contacto administrativo y resolución.
- Calendario institucional.
- Lista de espera y gestión de estados.
- Dashboard administrativo con indicadores.
- Bitácora de auditoría para cambios de calificaciones.
- Descarga privada de archivos con control de acceso.

## Base de datos Turso

En `database/` hay dos scripts:

1. `01_reinicio_preservando_usuarios_estudiantes_docentes.sql`: conserva Usuarios, Estudiantes, Docentes y Carreras; elimina y reconstruye tablas académicas y operativas. Se pierden los datos de esas tablas operativas. Ejecutar una sola vez después de un respaldo.
2. `02_instalacion_completa_base_nueva.sql`: crea una base vacía completa, incluyendo las tablas base. Usar solamente en una base Turso nueva y vacía.

No ejecutar ambos scripts en la misma base. No ejecutar el script de reinicio sobre una base vacía porque presupone que ya existen Usuarios, Estudiantes, Docentes y Carreras.

## Variables de entorno

- `TURSO_DATABASE_URL`: URL `libsql://...`.
- `TURSO_AUTH_TOKEN`: token de Turso.
- `ADMIN_USER`: usuario inicial de administración.
- `ADMIN_PASSWORD`: contraseña inicial segura del administrador.
- `ORBITA_STORAGE_PATH`: ruta privada para documentos, comprobantes y fotos.

## Archivos privados en Render

Los archivos no se guardan dentro de `static`. `render.yaml` define un Persistent Disk en `/var/data` y `ORBITA_STORAGE_PATH=/var/data/orbita`. Si ya tienes un servicio Render creado manualmente, confirma que tenga un disco persistente montado en `/var/data`; los archivos guardados en el filesystem efímero se pierden al redeploy.

## Compilar localmente

Requiere JDK 21 y Maven:

```bash
mvn clean package
mvn spring-boot:run
```

Configura las variables de Turso antes de iniciar. Haz una copia de seguridad de la base antes de ejecutar cualquier script destructivo.


## Actualización de portales y cuotas por sección

- El menú estudiantil separa Mis pagos, Mi expediente y Soporte y evaluaciones.
- El menú administrativo separa Finanzas y cuotas, Expedientes y Soporte y seguimiento.
- Las cuotas se asignan a una sección académica; las notificaciones se envían únicamente a estudiantes matriculados activamente en esa sección.
- Para bases existentes, ejecutar `database/actualizardbnuevo.sql` una sola vez. Este script conserva las cuotas y comprobantes históricos; las cuotas antiguas quedan con `seccion_id` NULL porque antes eran generales.
- Para instalaciones nuevas, usar un script de instalación actualizado que define `Cuotas.seccion_id`.
- Los reportes Jasper se muestran en un visor integrado dentro de la misma página; no se abre una pestaña nueva.

### Nota de migración de cuotas por clase

Para una base Turso ya creada, copiar y ejecutar `database/actualizardbnuevo.sql` una sola vez. La migración reconstruye únicamente `Cuotas` y `PagosCuota` para poder cambiar la unicidad de cuota global a cuota por sección, conservando las filas existentes. Las cuotas antiguas quedan sin sección (`seccion_id = NULL`) y se mantienen como histórico; las nuevas cuotas deben asociarse a una sección activa.
