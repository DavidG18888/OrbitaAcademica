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

import java.util.*;

@Controller
@RequestMapping("/estudiante")
public class EstudianteController {

    private final TursoDAO dao;
    private final JasperPrintService jasper;
    private final PrivateFileService files;

    public EstudianteController(TursoDAO dao, JasperPrintService jasper, PrivateFileService files) {
        this.dao = dao;
        this.jasper = jasper;
        this.files = files;
    }

    private static final String MIS =
            "SELECT m.id AS matricula, sc.id AS seccion_id, m.estado AS m_estado, a.codigo, a.nombre AS asignatura, COALESCE(a.uv, 0) AS uv, sc.codigo AS seccion, "
            + "p.id AS periodo_id, p.nombre AS periodo, sc.dia, sc.hora_inicio, sc.hora_fin, sc.aula, "
            + "(SELECT GROUP_CONCAT(h.dia_semana || '|' || h.hora_inicio || '|' || h.hora_fin || '|' || COALESCE(h.aula,''), ';') FROM HorariosSeccion h WHERE h.seccion_id=sc.id AND h.dia_semana IS NOT NULL) AS bloques, "
            + "d.nombre || ' ' || d.apellido AS docente, "
            + "c.parcial1, c.parcial2, c.parcial3, c.puntos_extra, c.fecha_cierre, " + Util.PROM + " AS promedio, "
            + "(SELECT COUNT(*) FROM Asistencia x WHERE x.matricula_id = m.id) AS clases, "
            + "(SELECT COUNT(*) FROM Asistencia x WHERE x.matricula_id = m.id AND x.estado IN ('P','J')) AS asistidas, "
            + "(SELECT COUNT(*) FROM TareasSeccion t WHERE t.seccion_id = sc.id) AS total_tareas "
            + "FROM Matriculas m JOIN Estudiantes e ON e.id = m.estudiante_id JOIN Usuarios u ON u.id = e.usuario_id "
            + "JOIN Secciones sc ON sc.id = m.seccion_id LEFT JOIN Asignaturas a ON a.id = sc.asignatura_id "
            + "LEFT JOIN Periodos p ON p.id = sc.periodo_id LEFT JOIN Docentes d ON d.id = sc.docente_id "
            + "LEFT JOIN Calificaciones c ON c.matricula_id = m.id WHERE u.usuario = ? ORDER BY p.id DESC, a.nombre";

    private List<Map<String, Object>> todas(HttpSession s) {
        List<Map<String, Object>> l = dao.consultar(MIS, s.getAttribute("usuario"));
        Util.marcar(l);
        return l;
    }

    private List<Map<String, Object>> actuales(List<Map<String, Object>> todas) {
        List<Map<String, Object>> act = todas.stream().filter(r -> "ACTIVA".equals(r.get("m_estado"))).toList();
        if (act.isEmpty()) return act;
        Object per = act.get(0).get("periodo_id");
        return act.stream().filter(r -> Objects.equals(r.get("periodo_id"), per)).toList();
    }

    private static double sumUv(List<Map<String, Object>> l) {
        return l.stream().mapToDouble(r -> Optional.ofNullable(Util.num(r.get("uv"))).orElse(0.0)).sum();
    }

    @GetMapping({"", "/"})
    public String panel(Model m, HttpSession s) {
        List<Map<String, Object>> todas = todas(s);
        List<Map<String, Object>> act = actuales(todas);
        List<Double> proms = todas.stream().filter(r -> "ACTIVA".equals(r.get("m_estado")))
                .map(r -> Util.num(r.get("promedio"))).filter(Objects::nonNull).toList();
        double clases = act.stream().mapToDouble(r -> Optional.ofNullable(Util.num(r.get("clases"))).orElse(0.0)).sum();
        double asist = act.stream().mapToDouble(r -> Optional.ofNullable(Util.num(r.get("asistidas"))).orElse(0.0)).sum();
        List<Map<String, Object>> yo = dao.consultar(
                "SELECT e.codigo, e.nombre || ' ' || e.apellido AS nombre, c.nombre AS carrera FROM Estudiantes e JOIN Usuarios u ON u.id = e.usuario_id "
                + "LEFT JOIN Carreras c ON c.id = e.carrera_id WHERE u.usuario = ?", s.getAttribute("usuario"));
        Map<String, Object> perfil = yo.isEmpty() ? new HashMap<>() : yo.get(0);
        String carrera = perfil.get("carrera") == null ? null : String.valueOf(perfil.get("carrera"));
        Double prom = proms.isEmpty() ? null : Math.round(proms.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0;
        Long asi = clases == 0 ? null : Math.round(100 * asist / clases);
        m.addAttribute("sub", carrera != null ? carrera + " · Código " + perfil.get("codigo") : "Hoy es " + Util.fechaLarga());
        m.addAttribute("pProm", prom == null ? 0 : prom);
        m.addAttribute("tProm", prom == null ? "—" : String.valueOf(prom));
        m.addAttribute("pAsist", asi == null ? 0 : asi);
        m.addAttribute("tAsist", asi == null ? "—" : asi + "%");
        m.addAttribute("saludo", Util.saludo() + ", " + s.getAttribute("nombre"));
        m.addAttribute("fecha", Util.fechaLarga());
        m.addAttribute("actuales", act);
        m.addAttribute("uv", Math.round(sumUv(act)));
        m.addAttribute("promedio", prom);
        m.addAttribute("asistencia", asi);
        m.addAttribute("hoy", Util.semana(act).getOrDefault(Util.hoy(), List.of()));
        m.addAttribute("eventos", dao.consultar("SELECT * FROM EventosAcademicos WHERE COALESCE(estado,'ACTIVO') = 'ACTIVO' ORDER BY fecha_inicio ASC LIMIT 6"));
        return "estudiante/panel";
    }

    @GetMapping("/constancia")
    public ResponseEntity<byte[]> constancia(HttpSession s) {
        List<Map<String, Object>> perfil = dao.consultar("SELECT e.codigo,e.nombre||' '||e.apellido AS estudiante,c.nombre AS carrera FROM Estudiantes e JOIN Usuarios u ON u.id=e.usuario_id LEFT JOIN Carreras c ON c.id=e.carrera_id WHERE u.usuario=?", s.getAttribute("usuario"));
        if (perfil.isEmpty()) return ResponseEntity.notFound().build();
        List<Map<String, Object>> filas = dao.consultar("SELECT a.codigo,a.nombre AS asignatura,sc.codigo AS seccion,p.nombre AS periodo,d.nombre||' '||d.apellido AS docente FROM Matriculas ma JOIN Estudiantes e ON e.id=ma.estudiante_id JOIN Usuarios u ON u.id=e.usuario_id JOIN Secciones sc ON sc.id=ma.seccion_id JOIN Asignaturas a ON a.id=sc.asignatura_id JOIN Periodos p ON p.id=sc.periodo_id JOIN Docentes d ON d.id=sc.docente_id WHERE u.usuario=? AND ma.estado='ACTIVA' ORDER BY a.nombre", s.getAttribute("usuario"));
        Map<String, Object> datos = perfil.get(0);
        filas.forEach(r -> {
            r.put("nombre", r.get("asignatura"));
            r.put("codigo", r.get("codigo"));
            r.put("periodo", r.get("periodo"));
        });
        byte[] pdf = jasper.generar("Constancia de matrícula", "Estudiante: " + datos.get("estudiante") + " · Código: " + datos.get("codigo") + " · Carrera: " + datos.get("carrera"), filas);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=constancia-matricula.pdf").body(pdf);
    }

    @GetMapping("/oferta")
    public String oferta(Model m, HttpSession s) {
        List<Map<String, Object>> estudiante = dao.consultar("SELECT id FROM Estudiantes WHERE usuario_id=(SELECT id FROM Usuarios WHERE usuario=?)", s.getAttribute("usuario"));
        if (estudiante.isEmpty()) return "redirect:/estudiante";
        long estudianteId = Long.parseLong(String.valueOf(estudiante.get(0).get("id")));
        String hoy = Util.hoyFecha().toString();

        // Excluir:
        // 1) Secciones cuya fecha de cierre de matrícula (matricula_fin) ya venció.
        // 2) Asignaturas que el estudiante YA APROBÓ (nota >= 70 y notas cerradas) en cualquier período.
        // Si la reprobó, la retiró, no se presentó o nunca la llevó, sí le aparece disponible.
        List<Map<String, Object>> oferta = dao.consultar(
                "SELECT sc.id, sc.codigo AS seccion, a.codigo AS codigo, a.nombre AS asignatura, COALESCE(a.uv,0) AS uv, COALESCE(sc.aula,'Por definir') AS aula, "
                + "sc.matricula_inicio, sc.matricula_fin, d.nombre || ' ' || d.apellido AS docente, p.nombre AS periodo, sc.cupo, "
                + "(SELECT COUNT(*) FROM Matriculas mm WHERE mm.seccion_id=sc.id AND mm.estado='ACTIVA') AS inscritos, "
                + "COALESCE((SELECT GROUP_CONCAT(COALESCE(h.dia_semana,h.fecha_especifica) || ' ' || h.hora_inicio || '–' || h.hora_fin, ' · ') FROM HorariosSeccion h WHERE h.seccion_id=sc.id), sc.dia || ' ' || sc.hora_inicio || '–' || sc.hora_fin) AS horario, "
                + "(SELECT COUNT(*) FROM Matriculas mine WHERE mine.seccion_id=sc.id AND mine.estudiante_id=? AND mine.estado='ACTIVA') AS ya_matriculado, "
                + "(SELECT COUNT(*) FROM Matriculas prev JOIN Secciones psc ON psc.id=prev.seccion_id JOIN Calificaciones pc ON pc.matricula_id=prev.id "
                + " WHERE prev.estudiante_id=? AND psc.asignatura_id=sc.asignatura_id AND prev.estado='ACTIVA' AND pc.fecha_cierre IS NOT NULL "
                + " AND MIN(100.0, COALESCE(pc.parcial1,0)+COALESCE(pc.parcial2,0)+COALESCE(pc.parcial3,0)+COALESCE(pc.puntos_extra,0)) < 70) AS reprobada_antes "
                + "FROM Secciones sc JOIN Asignaturas a ON a.id=sc.asignatura_id JOIN Docentes d ON d.id=sc.docente_id JOIN Periodos p ON p.id=sc.periodo_id "
                + "WHERE COALESCE(sc.estado,'ACTIVA')='ACTIVA' AND COALESCE(p.estado,'ACTIVO')='ACTIVO' "
                + "AND (sc.matricula_fin IS NULL OR sc.matricula_fin = '' OR sc.matricula_fin >= ?) "
                + "AND NOT EXISTS ("
                + "  SELECT 1 FROM Matriculas apm JOIN Secciones apsc ON apsc.id=apm.seccion_id JOIN Calificaciones apc ON apc.matricula_id=apm.id "
                + "  WHERE apm.estudiante_id=? AND apsc.asignatura_id=sc.asignatura_id AND apm.estado='ACTIVA' AND apc.fecha_cierre IS NOT NULL "
                + "  AND MIN(100.0, COALESCE(apc.parcial1,0)+COALESCE(apc.parcial2,0)+COALESCE(apc.parcial3,0)+COALESCE(apc.puntos_extra,0)) >= 70"
                + ") "
                + "ORDER BY a.nombre, sc.codigo", estudianteId, estudianteId, hoy, estudianteId);

        for (Map<String, Object> fila : oferta) {
            fila.put("ya_matriculado", numeroLong(fila.get("ya_matriculado")));
            fila.put("inscritos", numeroLong(fila.get("inscritos")));
            fila.put("cupo", numeroLong(fila.get("cupo")));
            fila.put("reprobada_antes", numeroLong(fila.get("reprobada_antes")));
            Object mIni = fila.get("matricula_inicio");
            fila.put("aun_no_abre", mIni != null && !mIni.toString().isBlank() && hoy.compareTo(mIni.toString()) < 0);
        }

        m.addAttribute("oferta", oferta);
        return "estudiante/oferta";
    }

    private long numeroLong(Object valor) {
        if (valor == null) return 0L;
        try {
            return Long.parseLong(valor.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    @PostMapping("/oferta/{id}/matricular")
    public String matricularOferta(@PathVariable long id, HttpSession s, RedirectAttributes ra) {
        try {
            List<Map<String, Object>> est = dao.consultar("SELECT id FROM Estudiantes WHERE usuario_id=(SELECT id FROM Usuarios WHERE usuario=?)", s.getAttribute("usuario"));
            if (est.isEmpty()) throw new IllegalArgumentException("No se encontró el expediente del estudiante.");
            long estudianteId = Long.parseLong(String.valueOf(est.get(0).get("id")));
            List<Map<String, Object>> sec = dao.consultar("SELECT id,asignatura_id,cupo,matricula_inicio,matricula_fin,estado FROM Secciones WHERE id=?", id);
            if (sec.isEmpty() || !"ACTIVA".equalsIgnoreCase(String.valueOf(sec.get(0).get("estado")))) {
                throw new IllegalArgumentException("La sección no está disponible.");
            }
            String hoy = Util.hoyFecha().toString();
            Object abre = sec.get(0).get("matricula_inicio"), cierra = sec.get(0).get("matricula_fin");
            if (abre != null && !abre.toString().isBlank() && hoy.compareTo(abre.toString()) < 0) {
                throw new IllegalArgumentException("La matrícula todavía no está abierta.");
            }
            if (cierra != null && !cierra.toString().isBlank() && hoy.compareTo(cierra.toString()) > 0) {
                throw new IllegalArgumentException("El período de matrícula ya cerró.");
            }

            long yaAprobada = Long.parseLong(String.valueOf(dao.consultar(
                    "SELECT COUNT(*) AS n FROM Matriculas apm JOIN Secciones apsc ON apsc.id=apm.seccion_id JOIN Calificaciones apc ON apc.matricula_id=apm.id "
                    + "WHERE apm.estudiante_id=? AND apsc.asignatura_id=? AND apm.estado='ACTIVA' AND apc.fecha_cierre IS NOT NULL "
                    + "AND MIN(100.0, COALESCE(apc.parcial1,0)+COALESCE(apc.parcial2,0)+COALESCE(apc.parcial3,0)+COALESCE(apc.puntos_extra,0)) >= 70",
                    estudianteId, sec.get(0).get("asignatura_id")).get(0).get("n")));
            if (yaAprobada > 0) {
                throw new IllegalArgumentException("Ya aprobaste esta asignatura anteriormente.");
            }

            long cupo = Long.parseLong(String.valueOf(sec.get(0).get("cupo")));
            long inscritos = Long.parseLong(String.valueOf(dao.consultar("SELECT COUNT(*) AS n FROM Matriculas WHERE seccion_id=? AND estado='ACTIVA'", id).get(0).get("n")));
            if (inscritos >= cupo) throw new IllegalArgumentException("La sección ya no tiene cupos disponibles.");
            long duplicada = Long.parseLong(String.valueOf(dao.consultar("SELECT COUNT(*) AS n FROM Matriculas WHERE estudiante_id=? AND seccion_id=? AND estado='ACTIVA'", estudianteId, id).get(0).get("n")));
            if (duplicada > 0) throw new IllegalArgumentException("Ya está matriculado en esta sección.");
            long faltantes = Long.parseLong(String.valueOf(dao.consultar(
                    "SELECT COUNT(*) AS n FROM PrerrequisitosAsignatura pr WHERE pr.asignatura_id=(SELECT asignatura_id FROM Secciones WHERE id=?) "
                    + "AND NOT EXISTS (SELECT 1 FROM Matriculas pm JOIN Secciones ps ON ps.id=pm.seccion_id JOIN Calificaciones pc ON pc.matricula_id=pm.id "
                    + "WHERE pm.estudiante_id=? AND ps.asignatura_id=pr.prerrequisito_id AND pc.fecha_cierre IS NOT NULL "
                    + "AND MIN(100.0,COALESCE(pc.parcial1,0)+COALESCE(pc.parcial2,0)+COALESCE(pc.parcial3,0)+COALESCE(pc.puntos_extra,0))>=70)", id, estudianteId).get(0).get("n")));
            if (faltantes > 0) throw new IllegalArgumentException("No cumple todos los prerrequisitos de esta asignatura.");
            validarCruceHorario(estudianteId, id);
            dao.ejecutarSQL("INSERT INTO Matriculas(estudiante_id,seccion_id,estado) VALUES(?,?,'ACTIVA') ON CONFLICT(estudiante_id,seccion_id) DO UPDATE SET estado='ACTIVA', fecha=date('now'), fecha_retiro=NULL, motivo_retiro=NULL", estudianteId, id);
            ra.addFlashAttribute("ok", "Matrícula realizada correctamente.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/estudiante/oferta";
    }

    private void validarCruceHorario(long estudianteId, long seccionId) {
        List<Map<String, Object>> nuevos = dao.consultar("SELECT dia_semana,fecha_especifica,hora_inicio,hora_fin FROM HorariosSeccion WHERE seccion_id=?", seccionId);
        List<Map<String, Object>> existentes = dao.consultar(
                "SELECT h.dia_semana,h.fecha_especifica,h.hora_inicio,h.hora_fin FROM HorariosSeccion h "
                + "JOIN Matriculas m ON m.seccion_id=h.seccion_id WHERE m.estudiante_id=? AND m.estado='ACTIVA'", estudianteId);
        for (Map<String, Object> n : nuevos) {
            for (Map<String, Object> e : existentes) {
                String nd = String.valueOf(n.get("dia_semana")), ed = String.valueOf(e.get("dia_semana"));
                String nf = String.valueOf(n.get("fecha_especifica")), ef = String.valueOf(e.get("fecha_especifica"));
                boolean mismoDia = !"null".equals(nf) && !"null".equals(ef) && nf.equals(ef);
                if (!"null".equals(nd) && nd.equals(ed)) mismoDia = true;
                if (!"null".equals(nf) && !"null".equals(ed)) {
                    try { mismoDia = java.time.LocalDate.parse(nf).getDayOfWeek().getValue() == diaNumero(ed); } catch (Exception ignored) {}
                }
                if (!"null".equals(ef) && !"null".equals(nd)) {
                    try { mismoDia = java.time.LocalDate.parse(ef).getDayOfWeek().getValue() == diaNumero(nd); } catch (Exception ignored) {}
                }
                if (!mismoDia) continue;
                String ni = String.valueOf(n.get("hora_inicio")), nfin = String.valueOf(n.get("hora_fin"));
                String ei = String.valueOf(e.get("hora_inicio")), efin = String.valueOf(e.get("hora_fin"));
                if (ni.compareTo(efin) < 0 && ei.compareTo(nfin) < 0)
                    throw new IllegalArgumentException("Existe un cruce de horario con otra asignatura matriculada.");
            }
        }
    }

    private int diaNumero(String dia) {
        return switch (dia) {
            case "Lunes" -> 1; case "Martes" -> 2; case "Miércoles" -> 3;
            case "Jueves" -> 4; case "Viernes" -> 5; case "Sábado" -> 6; case "Domingo" -> 7;
            default -> -1;
        };
    }

    @GetMapping("/asignaturas")
    public String asignaturas(Model m, HttpSession s) {
        m.addAttribute("actuales", actuales(todas(s)));
        return "estudiante/asignaturas";
    }

    // Espacio individual por clase/sección con sus tareas por parcial, descargas y entrega del estudiante
    @GetMapping("/asignatura/{seccionId}")
    public String aulaAsignatura(@PathVariable long seccionId, Model m, HttpSession s) {
        List<Map<String, Object>> est = dao.consultar("SELECT id FROM Estudiantes WHERE usuario_id=(SELECT id FROM Usuarios WHERE usuario=?)", s.getAttribute("usuario"));
        if (est.isEmpty()) return "redirect:/estudiante/asignaturas";
        long estudianteId = Long.parseLong(String.valueOf(est.get(0).get("id")));

        List<Map<String, Object>> claseInfo = dao.consultar(
                "SELECT sc.id, sc.codigo AS seccion, a.codigo, a.nombre AS asignatura, COALESCE(a.uv,0) AS uv, "
                + "p.nombre AS periodo, COALESCE(sc.aula,'Por definir') AS aula, d.nombre || ' ' || d.apellido AS docente, "
                + "c.parcial1, c.parcial2, c.parcial3, c.puntos_extra, c.fecha_cierre, " + Util.PROM + " AS promedio "
                + "FROM Matriculas ma JOIN Secciones sc ON sc.id=ma.seccion_id JOIN Asignaturas a ON a.id=sc.asignatura_id "
                + "LEFT JOIN Periodos p ON p.id=sc.periodo_id LEFT JOIN Docentes d ON d.id=sc.docente_id "
                + "LEFT JOIN Calificaciones c ON c.matricula_id=ma.id "
                + "WHERE ma.estudiante_id=? AND sc.id=? AND ma.estado='ACTIVA'", estudianteId, seccionId);
        if (claseInfo.isEmpty()) return "redirect:/estudiante/asignaturas";

        List<Map<String, Object>> tareas = dao.consultar(
                "SELECT t.*, en.id AS entrega_id, en.archivo_url AS entrega_url, en.nombre_archivo AS entrega_nombre, "
                + "en.comentario AS entrega_comentario, en.nota_obtenida, en.retroalimentacion, en.estado AS entrega_estado, en.entregada_en "
                + "FROM TareasSeccion t LEFT JOIN EntregasTarea en ON en.tarea_id=t.id AND en.estudiante_id=? "
                + "WHERE t.seccion_id=? ORDER BY t.parcial ASC, t.id DESC", estudianteId, seccionId);

        m.addAttribute("clase", claseInfo.get(0));
        m.addAttribute("tareas", tareas);
        return "estudiante/clase";
    }

    @PostMapping("/asignatura/{seccionId}/tarea/{tareaId}/entregar")
    public String entregarTarea(@PathVariable long seccionId, @PathVariable long tareaId,
                                @RequestParam(required = false) MultipartFile archivo,
                                @RequestParam(required = false) String comentario,
                                HttpSession s, RedirectAttributes ra) {
        try {
            List<Map<String, Object>> est = dao.consultar("SELECT id FROM Estudiantes WHERE usuario_id=(SELECT id FROM Usuarios WHERE usuario=?)", s.getAttribute("usuario"));
            if (est.isEmpty()) throw new IllegalArgumentException("Estudiante no encontrado.");
            long estudianteId = Long.parseLong(String.valueOf(est.get(0).get("id")));

            long matriculado = Long.parseLong(String.valueOf(dao.consultar(
                    "SELECT COUNT(*) AS n FROM Matriculas WHERE estudiante_id=? AND seccion_id=? AND estado='ACTIVA'", estudianteId, seccionId).get(0).get("n")));
            if (matriculado == 0) throw new IllegalArgumentException("No estás matriculado en esta sección.");

            List<Map<String, Object>> tInfo = dao.consultar("SELECT permite_entrega, titulo FROM TareasSeccion WHERE id=? AND seccion_id=?", tareaId, seccionId);
            if (tInfo.isEmpty()) throw new IllegalArgumentException("La tarea no existe.");
            if (!"1".equals(String.valueOf(tInfo.get(0).get("permite_entrega")))) {
                throw new IllegalArgumentException("Esta actividad no requiere subida de archivo.");
            }

            if ((archivo == null || archivo.isEmpty()) && (comentario == null || comentario.isBlank())) {
                throw new IllegalArgumentException("Adjunta un archivo (PDF o imagen) o escribe un comentario de entrega.");
            }

            String archivoUrl = null;
            String nombreArchivo = null;
            if (archivo != null && !archivo.isEmpty()) {
                archivoUrl = files.save(archivo, "entregas", "pdf", "jpg", "jpeg", "png", "webp");
                nombreArchivo = archivo.getOriginalFilename();
            }

            dao.ejecutarSQL("INSERT INTO EntregasTarea(tarea_id,estudiante_id,archivo_url,nombre_archivo,comentario,estado,entregada_en) VALUES(?,?,?,?,?,'ENTREGADA',CURRENT_TIMESTAMP) "
                    + "ON CONFLICT(tarea_id,estudiante_id) DO UPDATE SET "
                    + "archivo_url=COALESCE(excluded.archivo_url, EntregasTarea.archivo_url), "
                    + "nombre_archivo=COALESCE(excluded.nombre_archivo, EntregasTarea.nombre_archivo), "
                    + "comentario=excluded.comentario, estado='ENTREGADA', entregada_en=CURRENT_TIMESTAMP",
                    tareaId, estudianteId, archivoUrl, nombreArchivo,
                    (comentario == null || comentario.isBlank()) ? null : comentario.trim());

            ra.addFlashAttribute("ok", "Tu entrega fue enviada correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/estudiante/asignatura/" + seccionId;
    }

    @GetMapping("/calificaciones")
    public String calificaciones(Model m, HttpSession s) {
        m.addAttribute("actuales", actuales(todas(s)));
        return "estudiante/calificaciones";
    }

    @GetMapping("/horario")
    public String horario(Model m, HttpSession s) {
        List<Map<String, Object>> actuales = actuales(todas(s));
        m.addAttribute("semana", Util.semana(actuales));
        m.addAttribute("fechasEspeciales", dao.consultar(
                "SELECT h.fecha_especifica AS fecha,h.hora_inicio,h.hora_fin,COALESCE(h.aula,sc.aula) AS aula,a.nombre AS asignatura,sc.codigo AS seccion "
                + "FROM HorariosSeccion h JOIN Secciones sc ON sc.id=h.seccion_id JOIN Matriculas ma ON ma.seccion_id=sc.id "
                + "JOIN Estudiantes e ON e.id=ma.estudiante_id JOIN Usuarios u ON u.id=e.usuario_id LEFT JOIN Asignaturas a ON a.id=sc.asignatura_id "
                + "WHERE u.usuario=? AND ma.estado='ACTIVA' AND h.fecha_especifica IS NOT NULL ORDER BY h.fecha_especifica,h.hora_inicio", s.getAttribute("usuario")));
        return "estudiante/horario";
    }

    @GetMapping("/asistencia")
    public String asistencia(Model m, HttpSession s) {
        m.addAttribute("actuales", actuales(todas(s)));
        m.addAttribute("incidencias", dao.consultar(
                "SELECT x.fecha, x.estado, a.nombre AS asignatura FROM Asistencia x JOIN Matriculas m ON m.id = x.matricula_id "
                + "JOIN Estudiantes e ON e.id = m.estudiante_id JOIN Usuarios u ON u.id = e.usuario_id JOIN Secciones sc ON sc.id = m.seccion_id "
                + "LEFT JOIN Asignaturas a ON a.id = sc.asignatura_id WHERE u.usuario = ? AND x.estado <> 'P' ORDER BY x.fecha DESC LIMIT 30",
                s.getAttribute("usuario")));
        return "estudiante/asistencia";
    }

    @GetMapping("/historial")
    public String historial(Model m, HttpSession s) {
        Map<String, Map<String, Object>> g = new LinkedHashMap<>();
        double uvAprob = 0;
        List<Double> todos = new ArrayList<>();
        int aprobadas = 0, reprobadas = 0, noPresento = 0, cursando = 0;
        for (Map<String, Object> r : todas(s)) {
            String per = String.valueOf(r.get("periodo"));
            Map<String, Object> bloque = g.computeIfAbsent(per, k -> {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("periodo", k);
                b.put("filas", new ArrayList<Map<String, Object>>());
                return b;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) bloque.get("filas");
            filas.add(r);
        }
        for (Map<String, Object> r : todas(s)) {
            String estado = String.valueOf(r.get("estado"));
            if (estado.equals("Aprobada")) aprobadas++;
            else if (estado.equals("Reprobada")) reprobadas++;
            else if (estado.equals("No se presentó")) noPresento++;
            else cursando++;
        }
        for (Map<String, Object> b : g.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) b.get("filas");
            List<Double> ps = filas.stream().filter(r -> "ACTIVA".equals(r.get("m_estado")) && r.get("fecha_cierre") != null).map(r -> Util.num(r.get("promedio"))).filter(Objects::nonNull).toList();
            double ua = filas.stream().filter(r -> "ACTIVA".equals(r.get("m_estado")) && r.get("fecha_cierre") != null && Util.num(r.get("promedio")) != null && Util.num(r.get("promedio")) >= Util.PASA).mapToDouble(r -> Optional.ofNullable(Util.num(r.get("uv"))).orElse(0.0)).sum();
            b.put("promedio", ps.isEmpty() ? null : Math.round(ps.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0);
            b.put("uvAprobadas", Math.round(ua));
            uvAprob += ua;
            todos.addAll(ps);
        }
        m.addAttribute("historial", g.values());
        m.addAttribute("uvAprobadas", Math.round(uvAprob));
        m.addAttribute("indice", todos.isEmpty() ? null : Math.round(todos.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0);
        m.addAttribute("aprobadas", aprobadas);
        m.addAttribute("reprobadas", reprobadas);
        m.addAttribute("noPresento", noPresento);
        m.addAttribute("cursando", cursando);
        return "estudiante/historial";
    }

    @GetMapping("/historial/imprimir")
    public ResponseEntity<byte[]> imprimirHistorial(HttpSession s) {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map<String, Object> r : todas(s)) {
            Map<String, Object> f = new HashMap<>(r);
            f.put("estado", ("ACTIVA".equals(r.get("m_estado")) || "NO_SE_PRESENTO".equals(r.get("m_estado"))) ? r.get("estado") : "Retirada");
            filas.add(f);
        }
        byte[] pdf = jasper.generar("Historial académico", "Estudiante: " + s.getAttribute("nombre"), filas);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=historial-academico.pdf").body(pdf);
    }
}
