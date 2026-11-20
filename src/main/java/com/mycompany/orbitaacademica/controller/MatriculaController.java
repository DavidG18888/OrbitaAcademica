package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

// Administración de matrículas: control de cupo, duplicados, retiro y reactivación
@Controller
@RequestMapping("/admin/matriculas")
public class MatriculaController {

    private final TursoDAO dao;

    public MatriculaController(TursoDAO dao) {
        this.dao = dao;
    }

    private static final String LISTA =
            "SELECT m.id, e.codigo, e.nombre || ' ' || e.apellido AS estudiante, sc.codigo AS seccion, a.nombre AS asignatura, "
            + "p.nombre AS periodo, d.nombre || ' ' || d.apellido AS docente, m.fecha, m.estado "
            + "FROM Matriculas m JOIN Estudiantes e ON e.id = m.estudiante_id JOIN Secciones sc ON sc.id = m.seccion_id "
            + "LEFT JOIN Asignaturas a ON a.id = sc.asignatura_id LEFT JOIN Periodos p ON p.id = sc.periodo_id "
            + "LEFT JOIN Docentes d ON d.id = sc.docente_id ";

    @GetMapping
    public String listar(@RequestParam(required = false) String periodo, Model m) {
        boolean filtra = periodo != null && !periodo.isBlank();
        m.addAttribute("filas", filtra
                ? dao.consultar(LISTA + "WHERE sc.periodo_id = ? ORDER BY m.id DESC", periodo)
                : dao.consultar(LISTA + "ORDER BY m.id DESC"));
        m.addAttribute("periodos", dao.consultar("SELECT id, nombre FROM Periodos ORDER BY id DESC"));
        m.addAttribute("periodo", filtra ? periodo : "");
        m.addAttribute("estudiantes", dao.consultar(
                "SELECT id, codigo || ' · ' || nombre || ' ' || apellido AS nombre FROM Estudiantes WHERE estado = 'ACTIVO' ORDER BY apellido"));
        m.addAttribute("secciones", dao.consultar(
                "SELECT s.id, s.codigo || ' · ' || a.nombre || ' (' || (SELECT COUNT(*) FROM Matriculas x WHERE x.seccion_id = s.id AND x.estado = 'ACTIVA')"
                + " || '/' || COALESCE(s.cupo, 0) || ' cupos)' AS nombre FROM Secciones s JOIN Asignaturas a ON a.id = s.asignatura_id ORDER BY s.id DESC"));
        m.addAttribute("slug", "matriculas");
        return "admin/matriculas";
    }

    @PostMapping("/guardar")
    public String guardar(@RequestParam("estudiante_id") long est, @RequestParam("seccion_id") long sec, RedirectAttributes ra) {
        try {
            if (!dao.consultar("SELECT id FROM Matriculas WHERE estudiante_id = ? AND seccion_id = ?", est, sec).isEmpty()) {
                ra.addFlashAttribute("error", "El estudiante ya tiene matrícula en esa sección (si fue retirada, reactívela).");
                return "redirect:/admin/matriculas";
            }
            List<Map<String, Object>> s = dao.consultar(
                    "SELECT cupo, (SELECT COUNT(*) FROM Matriculas WHERE seccion_id = ? AND estado = 'ACTIVA') AS ins FROM Secciones WHERE id = ?", sec, sec);
            if (s.isEmpty()) {
                ra.addFlashAttribute("error", "La sección no existe.");
                return "redirect:/admin/matriculas";
            }
            Double cupo = com.mycompany.orbitaacademica.service.Util.num(s.get(0).get("cupo"));
            Double ins = com.mycompany.orbitaacademica.service.Util.num(s.get(0).get("ins"));
            if (cupo != null && ins != null && ins >= cupo) {
                ra.addFlashAttribute("error", "La sección no tiene cupos disponibles.");
                return "redirect:/admin/matriculas";
            }
            dao.ejecutarSQL("INSERT INTO Matriculas(estudiante_id, seccion_id) VALUES(?,?)", est, sec);
            ra.addFlashAttribute("ok", "Matrícula registrada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudo registrar la matrícula.");
        }
        return "redirect:/admin/matriculas";
    }

    @PostMapping("/{id}/retirar")
    public String alternar(@PathVariable long id, RedirectAttributes ra) {
        dao.ejecutarSQL("UPDATE Matriculas SET estado = CASE estado WHEN 'ACTIVA' THEN 'RETIRADA' ELSE 'ACTIVA' END WHERE id = ?", id);
        ra.addFlashAttribute("ok", "Estado de la matrícula actualizado.");
        return "redirect:/admin/matriculas";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable long id, RedirectAttributes ra) {
        dao.ejecutarSQL("DELETE FROM Calificaciones WHERE matricula_id = ?", id);
        dao.ejecutarSQL("DELETE FROM Asistencia WHERE matricula_id = ?", id);
        dao.ejecutarSQL("DELETE FROM Matriculas WHERE id = ?", id);
        ra.addFlashAttribute("ok", "Matrícula eliminada junto con sus notas y asistencia.");
        return "redirect:/admin/matriculas";
    }
}
