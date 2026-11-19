# Órbita Académica – actualización v4

Proyecto basado en Java 21, Spring Boot 3.5, Thymeleaf y Turso/libSQL.

## Cambios incluidos en esta entrega

- Eliminado el módulo y la navegación de Diagnóstico.
- Creador de secciones con múltiples bloques de horario.
- Cada bloque permite seleccionar varios días recurrentes o una fecha específica, hora de inicio, hora final y aula.
- Fechas personalizadas de clases y período de matrícula.
- Horarios múltiples conectados a `HorariosSeccion`; se conservan los campos antiguos de `Secciones` para compatibilidad.
- Oferta académica del estudiante con matrícula desde el portal.
- Validación de sección activa, período, fechas de matrícula, cupos, duplicados, prerrequisitos y cruces de horario.
- Calificaciones ponderadas: Parcial I 25, Parcial II 35, Parcial III 40. Puntos extra no negativos y nota final limitada a 100.
- Aprobación desde 70. La asignatura permanece `Cursando` hasta que el docente cierre las notas.
- Acción docente para cerrar las calificaciones de una sección cuando todos tengan los tres parciales.
- Historial con contadores de aprobadas, reprobadas, no se presentó y cursando; promedio solo de asignaturas cerradas.
- Esquema SQL ampliado para documentos, cuotas, pagos, notificaciones, tickets, respuestas, evaluaciones docentes, ausencias, contacto, perfil, prerrequisitos, lista de espera, bitácora y eventos.

## Archivos SQL

En `database/` están los siguientes scripts:

1. `orbita_academica_instalacion_completa_v4.sql`: crear una base nueva desde cero.
2. `orbita_academica_migracion_completa_v4.sql`: migrar una base que tiene el esquema original incluido en el proyecto. No elimina datos. Ejecutar una sola vez.
3. `parche_v3_a_v4.sql`: usar únicamente si ya ejecutaste la migración SQL v3 anterior. Ejecutar una sola vez.
4. `orbita_academica.sql`: copia del esquema completo de instalación v4.

**No ejecutes los tres scripts en la misma base.** Haz un respaldo de Turso antes de migrar. Elige el script que corresponda a la versión real de tu base.

## Configuración

Configura en Render las variables:

- `TURSO_DATABASE_URL`: URL `libsql://...` de tu base.
- `TURSO_AUTH_TOKEN`: token de acceso de Turso.

## Compilar y ejecutar localmente

Requiere JDK 21 y Maven. Desde la carpeta del proyecto:

```bash
mvn clean package
mvn spring-boot:run
```

## Importante sobre el alcance

Esta entrega conecta el flujo de secciones/horarios, oferta y matrícula estudiantil, ponderación de notas e historial académico. Las tablas para documentos, fotografías, comprobantes de pago, cuotas, tickets, notificaciones y evaluación docente quedan creadas como base de datos, pero sus pantallas, almacenamiento privado de archivos y flujos administrativos todavía requieren integración. Para archivos personales y comprobantes debe configurarse almacenamiento privado persistente (por ejemplo, un servicio de objetos); no es recomendable guardar esos documentos en el filesystem efímero de Render.

La compilación Maven no se pudo ejecutar en el entorno de preparación porque Maven no está instalado. Compila localmente y comparte cualquier error de consola para corregirlo.
