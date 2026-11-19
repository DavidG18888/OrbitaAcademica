package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import com.mycompany.orbitaacademica.service.Util;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

// Portal docente: panel, secciones, horario, calificaciones, asistencia y reportes de notas
@Controller
@RequestMapping("/docente")
public class DocenteController {

    private final TursoDAO dao;

    public DocenteController(TursoDAO dao) {
        this.dao = dao;
    }

    private static final String MIS =
            "SELECT s.id, s.codigo AS seccion, a.codigo AS codigo, a.nombre AS asignatura, p.nombre AS periodo, s.dia, s.hora_inicio, s.hora_fin, s.aula, s.cupo, "
            + "(SELECT COUNT(*) FROM Matriculas m WHERE m.seccion_id = s.id AND m.estado = 'ACTIVA') AS inscritos "
            + "FROM Secciones s JOIN Docentes d ON d.id = s.docente_id JOIN Usuarios u ON u.id = d.usuario_id "
            + "LEFT JOIN Asignaturas a ON a.id = s.asignatura_id LEFT JOIN Periodos p ON p.id = s.periodo_id WHERE u.usuario = ? ";

    private static final String ALUM =
            "SELECT m.id AS matricula, e.codigo, e.nombre || ' ' || e.apellido AS nombre, c.parcial1, c.parcial2, c.parcial3, " + Util.PROM + " AS promedio, "
            + "(SELECT COUNT(*) FROM Asistencia x WHERE x.matricula_id = m.id) AS clases, "
            + "(SELECT COUNT(*) FROM Asistencia x WHERE x.matricula_id = m.id AND x.estado IN ('P','J')) AS asistidas "
            + "FROM Matriculas m JOIN Estudiantes e ON e.id = m.estudiante_id LEFT JOIN Calificaciones c ON c.matricula_id = m.id "
            + "WHERE m.seccion_id = ? AND m.estado = 'ACTIVA' ORDER BY e.apellido, e.nombre";

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
        return "docente/horario";
    }

    // ---------- Estudiantes y calificaciones ----------

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
                Double n1 = nota(p.get("p1_" + mid)), n2 = nota(p.get("p2_" + mid)), n3 = nota(p.get("p3_" + mid));
                dao.ejecutarSQL("INSERT INTO Calificaciones(matricula_id, parcial1, parcial2, parcial3) VALUES(?,?,?,?) "
                        + "ON CONFLICT(matricula_id) DO UPDATE SET parcial1 = excluded.parcial1, parcial2 = excluded.parcial2, parcial3 = excluded.parcial3",
                        Long.parseLong(mid), n1, n2, n3);
            }
            ra.addFlashAttribute("ok", "Calificaciones guardadas.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Las notas deben ser números entre 0 y 100.");
        }
        return "redirect:/docente/seccion/" + id;
    }

    private Double nota(String v) {
        if (v == null || v.isBlank()) return null;
        double d = Double.parseDouble(v.trim());
        if (d < 0 || d > 100) throw new IllegalArgumentException("Fuera de rango");
        return d;
    }

    // ---------- Asistencia ----------

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

    // ---------- Reporte de notas ----------

    @GetMapping("/seccion/{id}/reporte")
    public String reporte(@PathVariable long id, Model m, HttpSession s) {
        Map<String, Object> sec = propia(id, s);
        if (sec == null) return "redirect:/docente";
        List<Map<String, Object>> al = dao.consultar(ALUM, id);
        Util.marcar(al);
        List<Double> pr = al.stream().map(a -> Util.num(a.get("promedio"))).filter(Objects::nonNull).toList();
        long ap = pr.stream().filter(x -> x >= Util.PASA).count();
        int[] dist = new int[4];
        for (double x : pr) dist[x < 65 ? 0 : x < 80 ? 1 : x < 90 ? 2 : 3]++;
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

    @GetMapping("/seccion/{id}/reporte.csv")
    public ResponseEntity<String> reporteCsv(@PathVariable long id, HttpSession s) {
        Map<String, Object> sec = propia(id, s);
        if (sec == null) return ResponseEntity.notFound().build();
        List<Map<String, Object>> al = dao.consultar(ALUM, id);
        Util.marcar(al);
        List<List<Object>> filas = new ArrayList<>();
        for (Map<String, Object> a : al) {
            filas.add(Arrays.asList(a.get("codigo"), a.get("nombre"), a.get("parcial1"), a.get("parcial2"), a.get("parcial3"),
                    a.get("promedio"), a.get("estado"), a.get("asist") == null ? "" : a.get("asist") + "%"));
        }
        String nombre = "notas_" + String.valueOf(sec.get("seccion")).replaceAll("[^A-Za-z0-9_-]", "_") + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(Util.csv(List.of("Código", "Estudiante", "Parcial 1", "Parcial 2", "Parcial 3", "Promedio", "Estado", "Asistencia"), filas));
    }
}
