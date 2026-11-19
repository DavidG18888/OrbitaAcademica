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
            + "p.id AS periodo_id, p.nombre AS periodo, sc.dia, sc.hora_inicio, sc.hora_fin, sc.aula, d.nombre || ' ' || d.apellido AS docente, "
            + "c.parcial1, c.parcial2, c.parcial3, " + Util.PROM + " AS promedio, "
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
        m.addAttribute("semana", Util.semana(actuales(todas(s))));
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
        for (Map<String, Object> b : g.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) b.get("filas");
            List<Double> ps = filas.stream().filter(r -> "ACTIVA".equals(r.get("m_estado"))).map(r -> Util.num(r.get("promedio"))).filter(Objects::nonNull).toList();
            double ua = filas.stream().filter(r -> "ACTIVA".equals(r.get("m_estado")) && Util.num(r.get("promedio")) != null && Util.num(r.get("promedio")) >= Util.PASA).mapToDouble(r -> Optional.ofNullable(Util.num(r.get("uv"))).orElse(0.0)).sum();
            b.put("promedio", ps.isEmpty() ? null : Math.round(ps.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0);
            b.put("uvAprobadas", Math.round(ua));
            uvAprob += ua;
            todos.addAll(ps);
        }
        m.addAttribute("historial", g.values());
        m.addAttribute("uvAprobadas", Math.round(uvAprob));
        m.addAttribute("indice", todos.isEmpty() ? null : Math.round(todos.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0);
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
