package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

// Portal docente (secciones y calificaciones) y portal estudiantil (consulta personal)
@Controller
public class PortalController {

    private final TursoDAO dao;

    public PortalController(TursoDAO dao) {
        this.dao = dao;
    }

    private static final String MIS_SECCIONES =
            "SELECT s.id, s.codigo AS seccion, a.nombre AS asignatura, p.nombre AS periodo, s.dia, s.hora_inicio, s.hora_fin, s.aula, "
            + "(SELECT COUNT(*) FROM Matriculas m WHERE m.seccion_id = s.id) AS inscritos "
            + "FROM Secciones s JOIN Docentes d ON d.id = s.docente_id JOIN Usuarios u ON u.id = d.usuario_id "
            + "LEFT JOIN Asignaturas a ON a.id = s.asignatura_id LEFT JOIN Periodos p ON p.id = s.periodo_id "
            + "WHERE u.usuario = ? ";

    @GetMapping("/docente")
    public String docente(Model m, HttpSession s) {
        m.addAttribute("secciones", dao.consultar(MIS_SECCIONES + "ORDER BY s.id DESC", s.getAttribute("usuario")));
        return "docente/panel";
    }

    @GetMapping("/docente/seccion/{id}")
    public String seccion(@PathVariable long id, Model m, HttpSession s, RedirectAttributes ra) {
        List<Map<String, Object>> sec = dao.consultar(MIS_SECCIONES + "AND s.id = ?", s.getAttribute("usuario"), id);
        if (sec.isEmpty()) return "redirect:/docente";

        m.addAttribute("sec", sec.get(0));
        m.addAttribute("alumnos", dao.consultar(
                "SELECT m.id AS matricula, e.codigo, e.nombre || ' ' || e.apellido AS nombre, c.parcial1, c.parcial2, c.parcial3 "
                + "FROM Matriculas m JOIN Estudiantes e ON e.id = m.estudiante_id "
                + "LEFT JOIN Calificaciones c ON c.matricula_id = m.id WHERE m.seccion_id = ? ORDER BY e.apellido", id));
        return "docente/seccion";
    }

    @PostMapping("/docente/seccion/{id}/notas")
    public String guardarNotas(@PathVariable long id, @RequestParam Map<String, String> p, HttpSession s, RedirectAttributes ra) {
        if (dao.consultar(MIS_SECCIONES + "AND s.id = ?", s.getAttribute("usuario"), id).isEmpty()) {
            return "redirect:/docente";
        }
        try {
            for (Map<String, Object> a : dao.consultar("SELECT id FROM Matriculas WHERE seccion_id = ?", id)) {
                String mid = String.valueOf(a.get("id"));
                Double n1 = nota(p.get("p1_" + mid)), n2 = nota(p.get("p2_" + mid)), n3 = nota(p.get("p3_" + mid));
                dao.ejecutarSQL("INSERT INTO Calificaciones(matricula_id, parcial1, parcial2, parcial3) VALUES(?,?,?,?) "
                        + "ON CONFLICT(matricula_id) DO UPDATE SET parcial1 = excluded.parcial1, parcial2 = excluded.parcial2, parcial3 = excluded.parcial3",
                        Long.parseLong(mid), n1, n2, n3);
            }
            ra.addFlashAttribute("ok", "Calificaciones guardadas.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Las notas deben estar entre 0 y 100.");
        }
        return "redirect:/docente/seccion/" + id;
    }

    private Double nota(String v) {
        if (v == null || v.isBlank()) return null;
        double d = Double.parseDouble(v.trim());
        if (d < 0 || d > 100) throw new IllegalArgumentException("Fuera de rango");
        return d;
    }

    @GetMapping("/estudiante")
    public String estudiante(Model m, HttpSession s) {
        m.addAttribute("datos", dao.consultar(
                "SELECT a.codigo, a.nombre AS asignatura, sc.codigo AS seccion, p.nombre AS periodo, sc.dia, sc.hora_inicio, sc.hora_fin, "
                + "c.parcial1, c.parcial2, c.parcial3, "
                + "CASE WHEN c.parcial1 IS NOT NULL AND c.parcial2 IS NOT NULL AND c.parcial3 IS NOT NULL "
                + "THEN ROUND((c.parcial1 + c.parcial2 + c.parcial3) / 3.0, 1) END AS promedio "
                + "FROM Matriculas m JOIN Estudiantes e ON e.id = m.estudiante_id JOIN Usuarios u ON u.id = e.usuario_id "
                + "JOIN Secciones sc ON sc.id = m.seccion_id LEFT JOIN Asignaturas a ON a.id = sc.asignatura_id "
                + "LEFT JOIN Periodos p ON p.id = sc.periodo_id LEFT JOIN Calificaciones c ON c.matricula_id = m.id "
                + "WHERE u.usuario = ? ORDER BY p.id DESC, a.nombre", s.getAttribute("usuario")));
        return "estudiante/panel";
    }
}
