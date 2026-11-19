package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import com.mycompany.orbitaacademica.service.InicioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

// Estado de las tablas en Turso y reparación (crea las que falten)
@Controller
@RequestMapping("/admin/diagnostico")
public class DiagnosticoController {

    private static final List<String> TABLAS = List.of("Usuarios", "Carreras", "Estudiantes", "Docentes", "Periodos",
            "Asignaturas", "Secciones", "Matriculas", "Calificaciones", "Asistencia");

    private final TursoDAO dao;
    private final InicioService inicio;

    public DiagnosticoController(TursoDAO dao, InicioService inicio) {
        this.dao = dao;
        this.inicio = inicio;
    }

    @GetMapping
    public String ver(Model m) {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (String t : TABLAS) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("tabla", t);
            try {
                f.put("ok", true);
                f.put("detalle", dao.consultar("SELECT COUNT(*) AS n FROM " + t).get(0).get("n") + " registros");
            } catch (Exception e) {
                f.put("ok", false);
                f.put("detalle", e.getMessage());
            }
            filas.add(f);
        }
        m.addAttribute("filas", filas);
        m.addAttribute("slug", "diagnostico");
        return "admin/diagnostico";
    }

    @PostMapping("/reparar")
    public String reparar(RedirectAttributes ra) {
        List<String> errores = inicio.preparar();
        if (errores.isEmpty()) {
            ra.addFlashAttribute("ok", "Tablas verificadas y creadas correctamente.");
        } else {
            ra.addFlashAttribute("error", "Algunas sentencias fallaron: " + String.join(" | ", errores));
        }
        return "redirect:/admin/diagnostico";
    }
}
