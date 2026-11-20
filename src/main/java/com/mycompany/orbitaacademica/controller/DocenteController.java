package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import com.mycompany.orbitaacademica.service.JasperPrintService;
import com.mycompany.orbitaacademica.service.PrivateFileService;
import com.mycompany.orbitaacademica.service.Util;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/docente")
public class DocenteController {

    private final TursoDAO dao;
    private final JasperPrintService jasper;
    private final PrivateFileService files;

    public DocenteController(TursoDAO dao, JasperPrintService jasper, PrivateFileService files) {
        this.dao = dao;
        this.jasper = jasper;
        this.files = files;
    }

    private static final String MIS =
            "SELECT s.id, s.codigo AS seccion, COALESCE(s.modalidad,'Presencial') AS modalidad, a.codigo AS codigo, a.nombre AS asignatura, p.nombre AS periodo, s.dia, s.hora_inicio, s.hora_fin, s.aula, s.cupo, "
            + "(SELECT GROUP_CONCAT(h.dia_semana || '|' || h.hora_inicio || '|' || h.hora_fin || '|' || COALESCE(h.aula,''), ';') FROM HorariosSeccion h WHERE h.seccion_id=s.id AND h.dia_semana IS NOT NULL) AS bloques, "
            + "(SELECT COUNT(*) FROM Matriculas m WHERE m.seccion_id = s.id AND m.estado = 'ACTIVA') AS inscritos, "
            + "(SELECT COUNT(*) FROM TareasSeccion t WHERE t.seccion_id = s.id) AS total_tareas "
            + "FROM Secciones s JOIN Docentes d ON d.id = s.docente_id JOIN Usuarios u ON u.id = d.usuario_id "
            + "LEFT JOIN Asignaturas a ON a.id = s.asignatura_id LEFT JOIN Periodos p ON p.id = s.periodo_id WHERE u.usuario = ? ";

    private static final String ALUM =
            "SELECT m.id AS matricula, e.id AS estudiante_id, e.codigo, e.nombre || ' ' || e.apellido AS nombre, m.estado AS m_estado, m.motivo_retiro, c.parcial1, c.parcial2, c.parcial3, c.puntos_extra, c.fecha_cierre, " + Util.PROM + " AS promedio, "
            + "(SELECT COUNT(*) FROM Asistencia x WHERE x.matricula_id = m.id) AS clases, "
            + "(SELECT COUNT(*) FROM Asistencia x WHERE x.matricula_id = m.id AND x.estado IN ('P','J')) AS asistidas "
            + "FROM Matriculas m JOIN Estudiantes e ON e.id = m.estudiante_id LEFT JOIN Calificaciones c ON c.matricula_id = m.id "
            + "WHERE m.seccion_id = ? AND m.estado IN ('ACTIVA','NO_SE_PRESENTO') ORDER BY CASE m.estado WHEN 'ACTIVA' THEN 0 ELSE 1 END, e.apellido, e.nombre";

    private List<Map<String, Object>> secciones(HttpSession s) {
        List<Map<String, Object>> l = dao.consultar(MIS + "ORDER BY s.id DESC", s.getAttribute("usuario"));
        Util.marcar(l);
        return l;
    }

    private Map<String, Object> propia(long id, HttpSession s) {
        List<Map<String, Object>> l = dao.consultar(MIS + "AND s.id = ?", s.getAttribute("usuario"), id);
        Util.marcar(l);
        if (l.isEmpty()) return null;
        l.get(0).put("titulo", l.get(0).get("asignatura") + " · " + l.get(0).get("seccion"));
        return l.get(0);
    }

    @GetMapping({"", "/"})
    public String panel(Model m, HttpSession s) {
        List<Map<String, Object>> secs = secciones(s);
        long alumnos = secs.stream().mapToLong(x -> Math.round(Util.num(x.get("inscritos")) == null ? 0 : Util.num(x.get("inscritos")))).sum();
        List<Map<String, Object>> hoy = Util.semana(secs).getOrDefault(Util.hoy(), List.of());
        Object pend = dao.consultar(
                "SELECT COUNT(*) AS n FROM Matriculas m JOIN Secciones sc ON sc.id = m.seccion_id JOIN Docentes d ON d.id = sc.docente_id "
                + "JOIN Usuarios u ON u.id = d.usuario_id LEFT JOIN Calificaciones c ON c.matricula_id = m.id "
                + "WHERE u.usuario = ? AND m.estado = 'ACTIVA' AND (c.id IS NULL OR c.parcial1 IS NULL OR c.parcial2 IS NULL OR c.parcial3 IS NULL)",
                s.getAttribute("usuario")).get(0).get("n");
        m.addAttribute("saludo", Util.saludo() + ", " + s.getAttribute("nombre"));
        m.addAttribute("sub", "Hoy es " + Util.fechaLarga() + ". Esto es lo que tiene en su órbita.");
        m.addAttribute("secciones", secs);
        m.addAttribute("alumnos", alumnos);
        m.addAttribute("pendientes", pend);
        m.addAttribute("hoy", hoy);
        m.addAttribute("eventos", dao.consultar("SELECT * FROM EventosAcademicos WHERE COALESCE(estado,'ACTIVO') = 'ACTIVO' ORDER BY fecha_inicio ASC LIMIT 6"));
        return "docente/panel";
    }

    @GetMapping("/secciones")
    public String lista(Model m, HttpSession s) {
        m.addAttribute("secciones", secciones(s));
        return "docente/secciones";
    }

    @GetMapping("/horario")
    public String horario(Model m, HttpSession s) {
        m.addAttribute("semana", Util.semana(secciones(s)));
        m.addAttribute("fechasEspeciales", dao.consultar(
                "SELECT h.fecha_especifica AS fecha,h.hora_inicio,h.hora_fin,COALESCE(h.aula,sc.aula) AS aula,a.nombre AS asignatura,sc.codigo AS seccion "
                + "FROM HorariosSeccion h JOIN Secciones sc ON sc.id=h.seccion_id JOIN Docentes d ON d.id=sc.docente_id JOIN Usuarios u ON u.id=d.usuario_id "
                + "LEFT JOIN Asignaturas a ON a.id=sc.asignatura_id WHERE u.usuario=? AND h.fecha_especifica IS NOT NULL ORDER BY h.fecha_especifica,h.hora_inicio", s.getAttribute("usuario")));
        return "docente/horario";
    }

    @GetMapping("/seccion/{id}")
    public String notas(@PathVariable long id, Model m, HttpSession s) {
        Map<String, Object> sec = propia(id, s);
        if (sec == null) return "redirect:/docente";
        List<Map<String, Object>> al = dao.consultar(ALUM, id);
        Util.marcar(al);
        m.addAttribute("sec", sec);
        m.addAttribute("alumnos", al);
        return "docente/seccion";
    }

    @PostMapping("/seccion/{id}/notas")
    public String guardarNotas(@PathVariable long id, @RequestParam Map<String, String> p, HttpSession s, RedirectAttributes ra) {
        if (propia(id, s) == null) return "redirect:/docente";
        try {
            for (Map<String, Object> a : dao.consultar("SELECT id FROM Matriculas WHERE seccion_id = ? AND estado = 'ACTIVA'", id)) {
                String mid = String.valueOf(a.get("id"));
                Double n1 = nota(p.get("p1_" + mid), 20), n2 = nota(p.get("p2_" + mid), 35), n3 = nota(p.get("p3_" + mid), 45);
                Double extra = nota(p.get("extra_" + mid), 100);
                List<Map<String, Object>> previo = dao.consultar("SELECT parcial1,parcial2,parcial3,puntos_extra FROM Calificaciones WHERE matricula_id=?", Long.parseLong(mid));
                String anterior = previo.isEmpty() ? "SIN NOTAS" : previo.get(0).toString();
                String nuevo = "P1=" + n1 + ", P2=" + n2 + ", P3=" + n3 + ", EXTRA=" + (extra == null ? 0 : extra);
                dao.ejecutarSQL("INSERT INTO Calificaciones(matricula_id, parcial1, parcial2, parcial3, puntos_extra) VALUES(?,?,?,?,?) "
                        + "ON CONFLICT(matricula_id) DO UPDATE SET parcial1=excluded.parcial1, parcial2=excluded.parcial2, parcial3=excluded.parcial3, puntos_extra=excluded.puntos_extra",
                        Long.parseLong(mid), n1, n2, n3, extra == null ? 0 : extra);
                dao.ejecutarSQL("INSERT INTO BitacoraSistema(usuario_id,accion,entidad,entidad_id,valor_anterior,valor_nuevo) SELECT id,'ACTUALIZAR_CALIFICACION','Matriculas',?,?,? FROM Usuarios WHERE usuario=?",
                        Long.parseLong(mid), anterior, nuevo, s.getAttribute("usuario"));
            }
            ra.addFlashAttribute("ok", "Calificaciones guardadas correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Parcial I máximo 20, Parcial II máximo 35, Parcial III máximo 45 y extras no negativos.");
        }
        return "redirect:/docente/seccion/" + id;
    }

    // Sincronizar notas acumuladas de tareas calificadas al cuadro de calificaciones de la sección
    @PostMapping("/seccion/{id}/sincronizar-tareas")
    public String sincronizarTareas(@PathVariable long id, HttpSession s, RedirectAttributes ra) {
        if (propia(id, s) == null) return "redirect:/docente";
        try {
            List<Map<String, Object>> mats = dao.consultar("SELECT id, estudiante_id FROM Matriculas WHERE seccion_id=? AND estado='ACTIVA'", id);
            int actualizados = 0;
            for (Map<String, Object> m : mats) {
                long mid = Long.parseLong(String.valueOf(m.get("id")));
                long estId = Long.parseLong(String.valueOf(m.get("estudiante_id")));

                Double s1 = sumaTareasParcial(id, estId, 1, 20.0);
                Double s2 = sumaTareasParcial(id, estId, 2, 35.0);
                Double s3 = sumaTareasParcial(id, estId, 3, 45.0);

                if (s1 != null || s2 != null || s3 != null) {
                    dao.ejecutarSQL("INSERT INTO Calificaciones(matricula_id, parcial1, parcial2, parcial3, puntos_extra) VALUES(?,?,?,?,0) "
                            + "ON CONFLICT(matricula_id) DO UPDATE SET "
                            + "parcial1 = COALESCE(?, Calificaciones.parcial1), "
                            + "parcial2 = COALESCE(?, Calificaciones.parcial2), "
                            + "parcial3 = COALESCE(?, Calificaciones.parcial3)",
                            mid, s1, s2, s3, s1, s2, s3);
                    actualizados++;
                }
            }
            ra.addFlashAttribute("ok", "Se sincronizaron las notas de tareas calificadas para " + actualizados + " estudiantes.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudieron sincronizar las tareas: " + e.getMessage());
        }
        return "redirect:/docente/seccion/" + id;
    }

    private Double sumaTareasParcial(long seccionId, long estudianteId, int parcial, double max) {
        List<Map<String, Object>> r = dao.consultar(
                "SELECT COUNT(en.id) AS cnt, COALESCE(SUM(en.nota_obtenida), 0) AS total "
                + "FROM EntregasTarea en JOIN TareasSeccion t ON t.id=en.tarea_id "
                + "WHERE t.seccion_id=? AND t.parcial=? AND en.estudiante_id=? AND en.nota_obtenida IS NOT NULL",
                seccionId, parcial, estudianteId);
        if (r.isEmpty()) return null;
        long cnt = Long.parseLong(String.valueOf(r.get(0).get("cnt")));
        if (cnt == 0) return null;
        double val = Util.num(r.get(0).get("total")) == null ? 0.0 : Util.num(r.get(0).get("total"));
        return Math.min(max, Math.round(val * 10.0) / 10.0);
    }

    @PostMapping("/seccion/{id}/cerrar-notas")
    public String cerrarNotas(@PathVariable long id, HttpSession s, RedirectAttributes ra) {
        if (propia(id, s) == null) return "redirect:/docente";
        try {
            long pendientes = Long.parseLong(String.valueOf(dao.consultar(
                    "SELECT COUNT(*) AS n FROM Matriculas m LEFT JOIN Calificaciones c ON c.matricula_id=m.id "
                    + "WHERE m.seccion_id=? AND m.estado='ACTIVA' AND (c.parcial1 IS NULL OR c.parcial2 IS NULL OR c.parcial3 IS NULL)", id).get(0).get("n")));
            if (pendientes > 0) throw new IllegalArgumentException("No se puede cerrar: todavía hay estudiantes activos sin los tres parciales.");
            dao.ejecutarSQL("UPDATE Calificaciones SET fecha_cierre=CURRENT_TIMESTAMP WHERE matricula_id IN (SELECT id FROM Matriculas WHERE seccion_id=? AND estado='ACTIVA')", id);
            ra.addFlashAttribute("ok", "Calificaciones cerradas. El historial académico ya refleja los estados finales.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/docente/seccion/" + id;
    }

    @PostMapping("/seccion/{id}/ausencia/{matriculaId}")
    public String reportarAusencia(@PathVariable long id, @PathVariable long matriculaId,
                                   @RequestParam(required = false) String motivo, HttpSession s, RedirectAttributes ra) {
        if (propia(id, s) == null) return "redirect:/docente";
        try {
            List<Map<String, Object>> mat = dao.consultar(
                    "SELECT m.id, e.usuario_id, a.nombre AS asignatura, sc.codigo AS seccion "
                    + "FROM Matriculas m JOIN Estudiantes e ON e.id=m.estudiante_id JOIN Secciones sc ON sc.id=m.seccion_id JOIN Asignaturas a ON a.id=sc.asignatura_id "
                    + "WHERE m.id=? AND m.seccion_id=? AND m.estado='ACTIVA'", matriculaId, id);
            if (mat.isEmpty()) throw new IllegalArgumentException("La matrícula no está activa en esta sección.");
            String motivoFinal = (motivo == null || motivo.isBlank()) ? "No se presentó a clases (reportado por docente)" : motivo.trim();
            long docenteId = Long.parseLong(String.valueOf(dao.consultar("SELECT d.id FROM Docentes d JOIN Usuarios u ON u.id=d.usuario_id WHERE u.usuario=?", s.getAttribute("usuario")).get(0).get("id")));

            dao.ejecutarSQL("UPDATE Matriculas SET estado='NO_SE_PRESENTO', fecha_retiro=date('now'), motivo_retiro=? WHERE id=?", motivoFinal, matriculaId);
            dao.ejecutarSQL("INSERT INTO RevisionesAusencia(matricula_id,reportado_por,motivo,estado_revision,fecha_revision) VALUES(?,?,?,'CONFIRMADA',CURRENT_TIMESTAMP) "
                    + "ON CONFLICT(matricula_id) DO UPDATE SET estado_revision='CONFIRMADA',motivo=excluded.motivo,reportado_por=excluded.reportado_por,fecha_revision=CURRENT_TIMESTAMP",
                    matriculaId, docenteId, motivoFinal);

            Object uidEst = mat.get(0).get("usuario_id");
            if (uidEst != null) {
                dao.ejecutarSQL("INSERT INTO Notificaciones(usuario_id,titulo,mensaje,enlace) VALUES(?,?,?,?)",
                        uidEst, "Clase retirada · No se presentó",
                        "El docente reportó retiro por inasistencia en " + mat.get(0).get("asignatura") + " (" + mat.get(0).get("seccion") + "). Motivo: " + motivoFinal,
                        "/estudiante/historial");
            }
            ra.addFlashAttribute("ok", "Clase retirada y marcada como 'No se presentó'.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/docente/seccion/" + id;
    }

    private Double nota(String v, double maximo) {
        if (v == null || v.isBlank()) return null;
        double d = Double.parseDouble(v.trim());
        if (d < 0 || d > maximo) throw new IllegalArgumentException("La nota excede el máximo permitido: " + maximo);
        return d;
    }

    @GetMapping("/seccion/{id}/tareas")
    public String tareas(@PathVariable long id, Model m, HttpSession s) {
        Map<String, Object> sec = propia(id, s);
        if (sec == null) return "redirect:/docente";

        List<Map<String, Object>> tareas = dao.consultar(
                "SELECT t.*, (SELECT COUNT(*) FROM EntregasTarea en WHERE en.tarea_id=t.id) AS total_entregas, "
                + "(SELECT COUNT(*) FROM EntregasTarea en WHERE en.tarea_id=t.id AND en.nota_obtenida IS NOT NULL) AS calificadas "
                + "FROM TareasSeccion t WHERE t.seccion_id=? ORDER BY t.parcial ASC, t.id DESC", id);

        double sumP1 = 0, sumP2 = 0, sumP3 = 0;
        for (Map<String, Object> t : tareas) {
            int p = Integer.parseInt(String.valueOf(t.get("parcial")));
            double pts = Util.num(t.get("puntaje")) == null ? 0 : Util.num(t.get("puntaje"));
            if (p == 1) sumP1 += pts;
            else if (p == 2) sumP2 += pts;
            else if (p == 3) sumP3 += pts;

            List<Map<String, Object>> entregas = dao.consultar(
                    "SELECT en.*, e.codigo, e.nombre || ' ' || e.apellido AS estudiante "
                    + "FROM EntregasTarea en JOIN Estudiantes e ON e.id=en.estudiante_id "
                    + "WHERE en.tarea_id=? ORDER BY en.entregada_en DESC", t.get("id"));
            t.put("entregas", entregas);
        }

        m.addAttribute("sec", sec);
        m.addAttribute("tareas", tareas);
        m.addAttribute("sumP1", Math.round(sumP1 * 100.0) / 100.0);
        m.addAttribute("sumP2", Math.round(sumP2 * 100.0) / 100.0);
        m.addAttribute("sumP3", Math.round(sumP3 * 100.0) / 100.0);
        m.addAttribute("dispP1", Math.max(0, Math.round((20.0 - sumP1) * 100.0) / 100.0));
        m.addAttribute("dispP2", Math.max(0, Math.round((35.0 - sumP2) * 100.0) / 100.0));
        m.addAttribute("dispP3", Math.max(0, Math.round((45.0 - sumP3) * 100.0) / 100.0));
        return "docente/tareas";
    }

    @PostMapping("/seccion/{id}/tareas")
    public String crearTarea(@PathVariable long id,
                             @RequestParam int parcial,
                             @RequestParam String titulo,
                             @RequestParam(required = false) String descripcion,
                             @RequestParam double puntaje,
                             @RequestParam(required = false) String fechaEntrega,
                             @RequestParam(defaultValue = "1") int permiteEntrega,
                             @RequestParam(required = false) MultipartFile archivo,
                             HttpSession s, RedirectAttributes ra) {
        Map<String, Object> sec = propia(id, s);
        if (sec == null) return "redirect:/docente";
        try {
            if (!List.of(1, 2, 3).contains(parcial)) throw new IllegalArgumentException("Seleccione un parcial válido (I, II o III).");
            if (puntaje <= 0) throw new IllegalArgumentException("El puntaje acumulado debe ser mayor que 0.");

            double maxParcial = parcial == 1 ? 20.0 : (parcial == 2 ? 35.0 : 45.0);
            Object actualObj = dao.consultar("SELECT COALESCE(SUM(puntaje),0) AS total FROM TareasSeccion WHERE seccion_id=? AND parcial=?", id, parcial).get(0).get("total");
            double acumuladoActual = Util.num(actualObj) == null ? 0.0 : Util.num(actualObj);

            if (acumuladoActual + puntaje > maxParcial + 0.0001) {
                double restante = Math.max(0, Math.round((maxParcial - acumuladoActual) * 100.0) / 100.0);
                throw new IllegalArgumentException("El Parcial " + parcial + " tiene un máximo de " + (int) maxParcial
                        + " pts. Ya asignaste " + acumuladoActual + " pts y solo te quedan " + restante + " pts disponibles.");
            }

            String archivoUrl = null;
            String nombreArchivo = null;
            if (archivo != null && !archivo.isEmpty()) {
                archivoUrl = files.save(archivo, "tareas", "pdf", "jpg", "jpeg", "png", "webp");
                nombreArchivo = archivo.getOriginalFilename();
            }

            dao.ejecutarSQL("INSERT INTO TareasSeccion(seccion_id,parcial,titulo,descripcion,puntaje,fecha_entrega,archivo_url,nombre_archivo,permite_entrega) VALUES(?,?,?,?,?,?,?,?,?)",
                    id, parcial, titulo.trim(),
                    (descripcion == null || descripcion.isBlank()) ? null : descripcion.trim(),
                    puntaje,
                    (fechaEntrega == null || fechaEntrega.isBlank()) ? null : fechaEntrega.trim(),
                    archivoUrl, nombreArchivo, permiteEntrega == 1 ? 1 : 0);

            List<Map<String, Object>> ests = dao.consultar(
                    "SELECT DISTINCT u.id FROM Usuarios u JOIN Estudiantes e ON e.usuario_id=u.id JOIN Matriculas m ON m.estudiante_id=e.id WHERE m.seccion_id=? AND m.estado='ACTIVA'", id);
            for (Map<String, Object> u : ests) {
                dao.ejecutarSQL("INSERT INTO Notificaciones(usuario_id,titulo,mensaje,enlace) VALUES(?,?,?,?)",
                        u.get("id"), "Nueva tarea · " + sec.get("asignatura"),
                        "Parcial " + parcial + ": " + titulo.trim() + " (" + puntaje + " pts)", "/estudiante/asignaturas");
            }

            ra.addFlashAttribute("ok", "Tarea publicada correctamente en el Parcial " + parcial + ".");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/docente/seccion/" + id + "/tareas";
    }

    @PostMapping("/seccion/{id}/tareas/{tareaId}/eliminar")
    public String eliminarTarea(@PathVariable long id, @PathVariable long tareaId, HttpSession s, RedirectAttributes ra) {
        if (propia(id, s) == null) return "redirect:/docente";
        dao.ejecutarSQL("DELETE FROM TareasSeccion WHERE id=? AND seccion_id=?", tareaId, id);
        ra.addFlashAttribute("ok", "Tarea eliminada.");
        return "redirect:/docente/seccion/" + id + "/tareas";
    }

    @PostMapping("/seccion/{id}/tareas/{tareaId}/calificar/{entregaId}")
    public String calificarEntrega(@PathVariable long id, @PathVariable long tareaId, @PathVariable long entregaId,
                                   @RequestParam double notaObtenida, @RequestParam(required = false) String retroalimentacion,
                                   HttpSession s, RedirectAttributes ra) {
        if (propia(id, s) == null) return "redirect:/docente";
        try {
            List<Map<String, Object>> tInfo = dao.consultar("SELECT puntaje, parcial, titulo FROM TareasSeccion WHERE id=? AND seccion_id=?", tareaId, id);
            if (tInfo.isEmpty()) throw new IllegalArgumentException("Tarea no encontrada.");
            double maxPts = Util.num(tInfo.get(0).get("puntaje"));
            if (notaObtenida < 0 || notaObtenida > maxPts) {
                throw new IllegalArgumentException("La calificación de esta tarea debe estar entre 0 y " + maxPts + " pts.");
            }
            dao.ejecutarSQL("UPDATE EntregasTarea SET nota_obtenida=?, retroalimentacion=?, estado='CALIFICADA', calificada_en=CURRENT_TIMESTAMP WHERE id=? AND tarea_id=?",
                    notaObtenida, (retroalimentacion == null || retroalimentacion.isBlank()) ? null : retroalimentacion.trim(), entregaId, tareaId);
            ra.addFlashAttribute("ok", "Entrega calificada correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/docente/seccion/" + id + "/tareas";
    }

    @GetMapping("/seccion/{id}/asistencia")
    public String asistencia(@PathVariable long id, @RequestParam(required = false) String fecha, Model m, HttpSession s) {
        Map<String, Object> sec = propia(id, s);
        if (sec == null) return "redirect:/docente";
        String f = fechaValida(fecha);
        List<Map<String, Object>> al = dao.consultar(
                "SELECT m.id AS matricula, e.codigo, e.nombre || ' ' || e.apellido AS nombre, a.estado AS marca, "
                + "(SELECT COUNT(*) FROM Asistencia x WHERE x.matricula_id = m.id) AS clases, "
                + "(SELECT COUNT(*) FROM Asistencia x WHERE x.matricula_id = m.id AND x.estado IN ('P','J')) AS asistidas "
                + "FROM Matriculas m JOIN Estudiantes e ON e.id = m.estudiante_id LEFT JOIN Asistencia a ON a.matricula_id = m.id AND a.fecha = ? "
                + "WHERE m.seccion_id = ? AND m.estado = 'ACTIVA' ORDER BY e.apellido, e.nombre", f, id);
        Util.marcar(al);
        m.addAttribute("sec", sec);
        m.addAttribute("fecha", f);
        m.addAttribute("alumnos", al);
        m.addAttribute("fechas", dao.consultar(
                "SELECT DISTINCT x.fecha FROM Asistencia x JOIN Matriculas mm ON mm.id = x.matricula_id WHERE mm.seccion_id = ? ORDER BY x.fecha DESC LIMIT 10", id));
        return "docente/asistencia";
    }

    @PostMapping("/seccion/{id}/asistencia")
    public String guardarAsistencia(@PathVariable long id, @RequestParam String fecha, @RequestParam Map<String, String> p, HttpSession s, RedirectAttributes ra) {
        if (propia(id, s) == null) return "redirect:/docente";
        String f = fechaValida(fecha);
        try {
            for (Map<String, Object> a : dao.consultar("SELECT id FROM Matriculas WHERE seccion_id = ? AND estado = 'ACTIVA'", id)) {
                String mid = String.valueOf(a.get("id"));
                String v = p.get("a_" + mid);
                if (v == null || !List.of("P", "A", "J").contains(v)) continue;
                dao.ejecutarSQL("INSERT INTO Asistencia(matricula_id, fecha, estado) VALUES(?,?,?) "
                        + "ON CONFLICT(matricula_id, fecha) DO UPDATE SET estado = excluded.estado", Long.parseLong(mid), f, v);
            }
            ra.addFlashAttribute("ok", "Asistencia del " + f + " guardada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudo guardar la asistencia.");
        }
        return "redirect:/docente/seccion/" + id + "/asistencia?fecha=" + f;
    }

    private String fechaValida(String f) {
        try { return LocalDate.parse(f).toString(); } catch (Exception e) { return Util.hoyFecha().toString(); }
    }

    @GetMapping("/seccion/{id}/reporte")
    public String reporte(@PathVariable long id, Model m, HttpSession s) {
        Map<String, Object> sec = propia(id, s);
        if (sec == null) return "redirect:/docente";
        List<Map<String, Object>> al = dao.consultar(ALUM, id);
        Util.marcar(al);
        List<Double> pr = al.stream().map(a -> Util.num(a.get("promedio"))).filter(Objects::nonNull).toList();
        long ap = pr.stream().filter(x -> x >= Util.PASA).count();
        int[] dist = new int[4];
        for (double x : pr) dist[x < 70 ? 0 : x < 80 ? 1 : x < 90 ? 2 : 3]++;
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("total", al.size());
        rep.put("calificados", pr.size());
        rep.put("aprobados", ap);
        rep.put("reprobados", pr.size() - ap);
        rep.put("media", pr.isEmpty() ? null : Math.round(pr.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0);
        rep.put("max", pr.isEmpty() ? null : Collections.max(pr));
        rep.put("min", pr.isEmpty() ? null : Collections.min(pr));
        rep.put("pctAprob", pr.isEmpty() ? 0 : Math.round(100.0 * ap / pr.size()));
        rep.put("dist", List.of(dist[0], dist[1], dist[2], dist[3]));
        rep.put("distMax", Math.max(1, Arrays.stream(dist).max().orElse(1)));
        m.addAttribute("sec", sec);
        m.addAttribute("alumnos", al);
        m.addAttribute("rep", rep);
        return "docente/reporte";
    }

    @GetMapping("/seccion/{id}/reporte/imprimir")
    public ResponseEntity<byte[]> imprimirReporte(@PathVariable long id, HttpSession s) {
        Map<String, Object> sec = propia(id, s);
        if (sec == null) return ResponseEntity.notFound().build();
        List<Map<String, Object>> alumnos = dao.consultar(ALUM, id);
        Util.marcar(alumnos);
        byte[] pdf = jasper.generar("Reporte de calificaciones", "Sección: " + sec.get("seccion") + " · " + sec.get("asignatura"), alumnos);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=reporte-seccion-" + id + ".pdf").body(pdf);
    }
}
