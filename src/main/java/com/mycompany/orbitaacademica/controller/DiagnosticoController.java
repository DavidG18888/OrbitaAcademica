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

// Estado de las tablas en Turso (existencia, registros y columnas) y reparación
@Controller
@RequestMapping("/admin/diagnostico")
public class DiagnosticoController {

    private final TursoDAO dao;
    private final InicioService inicio;

    public DiagnosticoController(TursoDAO dao, InicioService inicio) {
        this.dao = dao;
        this.inicio = inicio;
    }

    @GetMapping
    public String ver(Model m) {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map.Entry<String, List<String[]>> t : InicioService.esperadas().entrySet()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("tabla", t.getKey());
            try {
                Object n = dao.consultar("SELECT COUNT(*) AS n FROM " + t.getKey()).get(0).get("n");
                Set<String> reales = new HashSet<>();
                for (Map<String, Object> c : dao.consultar("PRAGMA table_info(" + t.getKey() + ")")) {
                    reales.add(String.valueOf(c.get("name")));
                }
                List<String> faltan = new ArrayList<>();
                for (String[] c : t.getValue()) {
                    if (!reales.contains(c[0])) faltan.add(c[0]);
                }
                f.put("ok", faltan.isEmpty());
                f.put("detalle", n + " registros"
                        + (faltan.isEmpty() ? "" : " · le faltan las columnas: " + String.join(", ", faltan))
                        + " · columnas actuales: " + String.join(", ", reales));
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
            ra.addFlashAttribute("ok", "Tablas y columnas verificadas. Revise abajo que todas aparezcan en OK.");
        } else {
            ra.addFlashAttribute("error", "Algunas sentencias fallaron: " + String.join(" | ", errores));
        }
        return "redirect:/admin/diagnostico";
    }
}
