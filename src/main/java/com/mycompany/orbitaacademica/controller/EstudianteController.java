package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import com.mycompany.orbitaacademica.service.Util;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.*;

// Portal estudiantil: panel, asignaturas, calificaciones, horario, asistencia, historial y descarga de notas
@Controller
@RequestMapping("/estudiante")
public class EstudianteController {

    private final TursoDAO dao;

    public EstudianteController(TursoDAO dao) {
        this.dao = dao;
    }

    private static final String MIS =
            "SELECT m.id AS matricula, m.estado AS m_estado, a.codigo, a.nombre AS asignatura, COALESCE(a.uv, 0) AS uv, sc.codigo AS seccion, "
            + "p.id AS periodo_id, p.nombre AS periodo, sc.dia, sc.hora_inicio, sc.hora_fin, sc.aula, (SELECT GROUP_CONCAT(h.dia_semana || '|' || h.hora_inicio || '|' || h.hora_fin || '|' || COALESCE(h.aula,''), ';') FROM HorariosSeccion h WHERE h.seccion_id=sc.id AND h.dia_semana IS NOT NULL) AS bloques, d.nombre || ' ' || d.apellido AS docente, "
            + "c.parcial1, c.parcial2, c.parcial3, c.puntos_extra, c.fecha_cierre, " + Util.PROM + " AS promedio, "
            + "(SELECT COUNT(*) FROM Asistencia x WHERE x.matricula_id = m.id) AS clases, "
            + "(SELECT COUNT(*) FROM Asistencia x WHERE x.matricula_id = m.id AND x.estado IN ('P','J')) AS asistidas "
            + "FROM Matriculas m JOIN Estudiantes e ON e.id = m.estudiante_id JOIN Usuarios u ON u.id = e.usuario_id "
            + "JOIN Secciones sc ON sc.id = m.seccion_id LEFT JOIN Asignaturas a ON a.id = sc.asignatura_id "
            + "LEFT JOIN Periodos p ON p.id = sc.periodo_id LEFT JOIN Docentes d ON d.id = sc.docente_id "
            + "LEFT JOIN Calificaciones c ON c.matricula_id = m.id WHERE u.usuario = ? ORDER BY p.id DESC, a.nombre";

    private List<Map<String, Object>> todas(HttpSession s) {
        List<Map<String, Object>> l = dao.consultar(MIS, s.getAttribute("usuario"));
        Util.marcar(l);
        return l;
    }

    // Matrículas activas del período más reciente
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
        m.addAttribute("promedio", proms.isEmpty() ? null : Math.round(proms.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0);
        m.addAttribute("asistencia", clases == 0 ? null : Math.round(100 * asist / clases));
        m.addAttribute("hoy", Util.semana(act).getOrDefault(Util.hoy(), List.of()));
        return "estudiante/panel";
    }

    @GetMapping("/oferta")
    public String oferta(Model m, HttpSession s) {
        List<Map<String,Object>> estudiante = dao.consultar("SELECT id FROM Estudiantes WHERE usuario_id=(SELECT id FROM Usuarios WHERE usuario=?)", s.getAttribute("usuario"));
        if (estudiante.isEmpty()) return "redirect:/estudiante";
        long estudianteId = Long.parseLong(String.valueOf(estudiante.get(0).get("id")));
        List<Map<String,Object>> oferta = dao.consultar(
                "SELECT sc.id, sc.codigo AS seccion, a.codigo AS codigo, a.nombre AS asignatura, d.nombre || ' ' || d.apellido AS docente, p.nombre AS periodo, sc.cupo, "
                + "(SELECT COUNT(*) FROM Matriculas mm WHERE mm.seccion_id=sc.id AND mm.estado='ACTIVA') AS inscritos, "
                + "COALESCE((SELECT GROUP_CONCAT(h.dia_semana || ' ' || h.hora_inicio || '–' || h.hora_fin, ' · ') FROM HorariosSeccion h WHERE h.seccion_id=sc.id), sc.dia || ' ' || sc.hora_inicio || '–' || sc.hora_fin) AS horario, "
                + "(SELECT COUNT(*) FROM Matriculas mine WHERE mine.seccion_id=sc.id AND mine.estudiante_id=? AND mine.estado='ACTIVA') AS ya_matriculado "
                + "FROM Secciones sc JOIN Asignaturas a ON a.id=sc.asignatura_id JOIN Docentes d ON d.id=sc.docente_id JOIN Periodos p ON p.id=sc.periodo_id "
                + "WHERE COALESCE(sc.estado,'ACTIVA')='ACTIVA' AND COALESCE(p.estado,'ACTIVO')='ACTIVO' ORDER BY a.nombre", estudianteId);

        // TursoDAO devuelve los valores como texto; Thymeleaf necesita números
        // para evaluar correctamente las condiciones >, == y < de la oferta.
        for (Map<String, Object> fila : oferta) {
            fila.put("ya_matriculado", numeroLong(fila.get("ya_matriculado")));
            fila.put("inscritos", numeroLong(fila.get("inscritos")));
            fila.put("cupo", numeroLong(fila.get("cupo")));
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

    @org.springframework.web.bind.annotation.PostMapping("/oferta/{id}/matricular")
    public String matricularOferta(@org.springframework.web.bind.annotation.PathVariable long id, HttpSession s, org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        try {
            List<Map<String,Object>> est = dao.consultar("SELECT id FROM Estudiantes WHERE usuario_id=(SELECT id FROM Usuarios WHERE usuario=?)", s.getAttribute("usuario"));
            if (est.isEmpty()) throw new IllegalArgumentException("No se encontró el expediente del estudiante.");
            long estudianteId = Long.parseLong(String.valueOf(est.get(0).get("id")));
            List<Map<String,Object>> sec = dao.consultar("SELECT id,cupo,matricula_inicio,matricula_fin,estado FROM Secciones WHERE id=?", id);
            if (sec.isEmpty() || !"ACTIVA".equalsIgnoreCase(String.valueOf(sec.get(0).get("estado")))) throw new IllegalArgumentException("La sección no está disponible.");
            String hoy = Util.hoyFecha().toString();
            Object abre = sec.get(0).get("matricula_inicio"), cierra = sec.get(0).get("matricula_fin");
            if (abre != null && hoy.compareTo(abre.toString()) < 0) throw new IllegalArgumentException("La matrícula todavía no está abierta.");
            if (cierra != null && hoy.compareTo(cierra.toString()) > 0) throw new IllegalArgumentException("El período de matrícula ya cerró.");
            long cupo = Long.parseLong(String.valueOf(sec.get(0).get("cupo")));
            long inscritos = Long.parseLong(String.valueOf(dao.consultar("SELECT COUNT(*) AS n FROM Matriculas WHERE seccion_id=? AND estado='ACTIVA'", id).get(0).get("n")));
            if (inscritos >= cupo) throw new IllegalArgumentException("La sección ya no tiene cupos disponibles.");
            long duplicada = Long.parseLong(String.valueOf(dao.consultar("SELECT COUNT(*) AS n FROM Matriculas WHERE estudiante_id=? AND seccion_id=? AND estado='ACTIVA'", estudianteId,id).get(0).get("n")));
            if (duplicada > 0) throw new IllegalArgumentException("Ya está matriculado en esta sección.");
            long faltantes = Long.parseLong(String.valueOf(dao.consultar(
                    "SELECT COUNT(*) AS n FROM PrerrequisitosAsignatura pr WHERE pr.asignatura_id=(SELECT asignatura_id FROM Secciones WHERE id=?) "
                    + "AND NOT EXISTS (SELECT 1 FROM Matriculas pm JOIN Secciones ps ON ps.id=pm.seccion_id JOIN Calificaciones pc ON pc.matricula_id=pm.id "
                    + "WHERE pm.estudiante_id=? AND ps.asignatura_id=pr.prerrequisito_id AND pc.fecha_cierre IS NOT NULL "
                    + "AND MIN(100.0,COALESCE(pc.parcial1,0)+COALESCE(pc.parcial2,0)+COALESCE(pc.parcial3,0)+COALESCE(pc.puntos_extra,0))>=70)", id, estudianteId).get(0).get("n")));
            if (faltantes > 0) throw new IllegalArgumentException("No cumple todos los prerrequisitos de esta asignatura.");
            validarCruceHorario(estudianteId, id);
            dao.ejecutarSQL("INSERT INTO Matriculas(estudiante_id,seccion_id,estado) VALUES(?,?,'ACTIVA')", estudianteId,id);
            ra.addFlashAttribute("ok", "Matrícula realizada correctamente.");
        } catch (Exception ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/estudiante/oferta";
    }

    private void validarCruceHorario(long estudianteId, long seccionId) {
        List<Map<String,Object>> nuevos = dao.consultar("SELECT dia_semana,fecha_especifica,hora_inicio,hora_fin FROM HorariosSeccion WHERE seccion_id=?", seccionId);
        List<Map<String,Object>> existentes = dao.consultar(
                "SELECT h.dia_semana,h.fecha_especifica,h.hora_inicio,h.hora_fin FROM HorariosSeccion h "
                + "JOIN Matriculas m ON m.seccion_id=h.seccion_id WHERE m.estudiante_id=? AND m.estado='ACTIVA'", estudianteId);
        for (Map<String,Object> n : nuevos) for (Map<String,Object> e : existentes) {
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

    private int diaNumero(String dia) {
        return switch (dia) { case "Lunes" -> 1; case "Martes" -> 2; case "Miércoles" -> 3; case "Jueves" -> 4; case "Viernes" -> 5; case "Sábado" -> 6; case "Domingo" -> 7; default -> -1; };
    }

    @GetMapping("/asignaturas")
    public String asignaturas(Model m, HttpSession s) {
        m.addAttribute("actuales", actuales(todas(s)));
        return "estudiante/asignaturas";
    }

    @GetMapping("/calificaciones")
    public String calificaciones(Model m, HttpSession s) {
        m.addAttribute("actuales", actuales(todas(s)));
        return "estudiante/calificaciones";
    }

    @GetMapping("/horario")
    public String horario(Model m, HttpSession s) {
        List<Map<String,Object>> actuales = actuales(todas(s));
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
        m.addAttribute("aprobadas", aprobadas); m.addAttribute("reprobadas", reprobadas);
        m.addAttribute("noPresento", noPresento); m.addAttribute("cursando", cursando);
        return "estudiante/historial";
    }

    @GetMapping("/notas.csv")
    public ResponseEntity<String> descargar(HttpSession s) {
        List<List<Object>> filas = new ArrayList<>();
        for (Map<String, Object> r : todas(s)) {
            filas.add(Arrays.asList(r.get("periodo"), r.get("codigo"), r.get("asignatura"), r.get("uv"), r.get("seccion"),
                    r.get("parcial1"), r.get("parcial2"), r.get("parcial3"), r.get("promedio"),
                    "ACTIVA".equals(r.get("m_estado")) ? r.get("estado") : "Retirada"));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("mis_notas.csv").build().toString())
                .body(Util.csv(List.of("Período", "Código", "Asignatura", "UV", "Sección", "Parcial 1", "Parcial 2", "Parcial 3", "Promedio", "Estado"), filas));
    }
}
