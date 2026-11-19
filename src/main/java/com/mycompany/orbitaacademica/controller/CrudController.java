package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

// Administración: estudiantes, docentes, carreras, asignaturas, períodos, secciones y matrículas
@Controller
@RequestMapping("/admin")
public class CrudController {

    public record Campo(String n, String label, String tipo, String sql) {}

    public record Ent(String tabla, String titulo, String lista, List<Campo> campos) {}

    private static Campo t(String n, String l) { return new Campo(n, l, "text", null); }
    private static Campo k(String n, String l, String tipo) { return new Campo(n, l, tipo, null); }
    private static Campo s(String n, String l, String sql) { return new Campo(n, l, "sel", sql); }
    private static final Campo ESTADO = k("estado", "Estado", "estado");
    private static final String CARRERAS = "SELECT id, nombre FROM Carreras ORDER BY nombre";

    private static final Map<String, Ent> ENT = Map.of(
        "estudiantes", new Ent("Estudiantes", "Estudiantes",
            "SELECT e.id, e.codigo AS Codigo, e.nombre || ' ' || e.apellido AS Nombre, c.nombre AS Carrera, e.estado AS Estado FROM Estudiantes e LEFT JOIN Carreras c ON c.id = e.carrera_id ORDER BY e.id DESC",
            List.of(t("codigo", "Código"), t("nombre", "Nombre"), t("apellido", "Apellido"), s("carrera_id", "Carrera", CARRERAS), t("correo", "Correo"), t("telefono", "Teléfono"), ESTADO)),
        "docentes", new Ent("Docentes", "Docentes",
            "SELECT id, codigo AS Codigo, nombre || ' ' || apellido AS Nombre, especialidad AS Especialidad, estado AS Estado FROM Docentes ORDER BY id DESC",
            List.of(t("codigo", "Código"), t("nombre", "Nombre"), t("apellido", "Apellido"), t("especialidad", "Especialidad"), t("correo", "Correo"), t("telefono", "Teléfono"), ESTADO)),
        "carreras", new Ent("Carreras", "Carreras",
            "SELECT id, codigo AS Codigo, nombre AS Nombre, estado AS Estado FROM Carreras ORDER BY nombre",
            List.of(t("codigo", "Código"), t("nombre", "Nombre"), ESTADO)),
        "asignaturas", new Ent("Asignaturas", "Asignaturas",
            "SELECT a.id, a.codigo AS Codigo, a.nombre AS Nombre, a.uv AS UV, c.nombre AS Carrera, (SELECT COUNT(*) FROM Secciones x WHERE x.asignatura_id = a.id) AS Secciones FROM Asignaturas a LEFT JOIN Carreras c ON c.id = a.carrera_id ORDER BY a.codigo",
            List.of(t("codigo", "Código"), t("nombre", "Nombre"), k("uv", "Unidades valorativas", "number"), s("carrera_id", "Carrera", CARRERAS), ESTADO)),
        "periodos", new Ent("Periodos", "Períodos académicos",
            "SELECT id, nombre AS Nombre, fecha_inicio AS Inicio, fecha_fin AS Fin, estado AS Estado FROM Periodos ORDER BY id DESC",
            List.of(t("nombre", "Nombre"), k("fecha_inicio", "Inicio", "date"), k("fecha_fin", "Fin", "date"), ESTADO))
    );

    private final TursoDAO dao;
    private final BCryptPasswordEncoder encoder;

    public CrudController(TursoDAO dao, BCryptPasswordEncoder encoder) {
        this.dao = dao;
        this.encoder = encoder;
    }

    @GetMapping("/{e:estudiantes|docentes|carreras|asignaturas|periodos}")
    public String listar(@PathVariable String e, Model m) {
        return vista(e, null, m);
    }

    @GetMapping("/{e:estudiantes|docentes|carreras|asignaturas|periodos}/{id}/editar")
    public String editar(@PathVariable String e, @PathVariable long id, Model m) {
        Ent en = ENT.get(e);
        if (en == null) return "redirect:/panel";
        List<Map<String, Object>> r = dao.consultar("SELECT * FROM " + en.tabla() + " WHERE id = ?", id);
        return vista(e, r.isEmpty() ? null : r.get(0), m);
    }

    @PostMapping("/{e:estudiantes|docentes|carreras|asignaturas|periodos}/guardar")
    public String guardar(@PathVariable String e, @RequestParam Map<String, String> p, RedirectAttributes ra) {
        Ent en = ENT.get(e);
        if (en == null) return "redirect:/panel";

        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (Campo c : en.campos()) {
            String v = p.get(c.n());
            cols.add(c.n());
            vals.add(v == null || v.isBlank() ? null : v.trim());
        }

        String id = p.get("id");
        try {
            if (id == null || id.isBlank()) {
                dao.ejecutarSQL("INSERT INTO " + en.tabla() + "(" + String.join(",", cols)
                        + ") VALUES(" + "?,".repeat(cols.size() - 1) + "?)", vals.toArray());
                crearUsuario(en, p);
            } else {
                vals.add(Long.parseLong(id));
                dao.ejecutarSQL("UPDATE " + en.tabla() + " SET " + String.join("=?,", cols) + "=? WHERE id = ?", vals.toArray());
            }
            ra.addFlashAttribute("ok", "Guardado correctamente.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "No se pudo guardar. Revise que no haya datos repetidos y que los campos sean válidos.");
        }
        return "redirect:/admin/" + e;
    }

    @PostMapping("/{e:estudiantes|docentes|carreras|asignaturas|periodos}/{id}/eliminar")
    public String eliminar(@PathVariable String e, @PathVariable long id, RedirectAttributes ra) {
        Ent en = ENT.get(e);
        if (en == null) return "redirect:/panel";
        try {
            dao.ejecutarSQL("DELETE FROM " + en.tabla() + " WHERE id = ?", id);
            ra.addFlashAttribute("ok", "Registro eliminado.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "No se pudo eliminar el registro.");
        }
        return "redirect:/admin/" + e;
    }

    // Al crear un estudiante o docente se crea su usuario (usuario = código, clave inicial = código)
    private void crearUsuario(Ent en, Map<String, String> p) {
        String rol = en.tabla().equals("Estudiantes") ? "ESTUDIANTE" : en.tabla().equals("Docentes") ? "DOCENTE" : null;
        if (rol == null) return;
        String cod = p.get("codigo").trim();
        try {
            dao.ejecutarSQL("INSERT INTO Usuarios(usuario,nombre,correo,clave,rol) VALUES(?,?,?,?,?)",
                    cod, p.get("nombre").trim() + " " + p.get("apellido").trim(), null, encoder.encode(cod), rol);
            dao.ejecutarSQL("UPDATE " + en.tabla() + " SET usuario_id = (SELECT id FROM Usuarios WHERE usuario = ?) WHERE codigo = ?", cod, cod);
        } catch (RuntimeException x) {
            dao.ejecutarSQL("DELETE FROM " + en.tabla() + " WHERE codigo = ?", cod);
            throw x;
        }
    }

    private String vista(String e, Map<String, Object> form, Model m) {
        Ent en = ENT.get(e);
        if (en == null) return "redirect:/panel";

        List<Map<String, Object>> filas = dao.consultar(en.lista());
        List<String> cols = filas.isEmpty() ? List.of()
                : filas.get(0).keySet().stream().filter(c -> !c.equals("id")).toList();

        Map<String, List<Map<String, Object>>> ops = new HashMap<>();
        for (Campo c : en.campos()) {
            if (c.sql() != null) ops.put(c.n(), dao.consultar(c.sql()));
        }

        m.addAttribute("slug", e);
        m.addAttribute("titulo", en.titulo());
        m.addAttribute("campos", en.campos());
        m.addAttribute("filas", filas);
        m.addAttribute("cols", cols);
        m.addAttribute("ops", ops);
        m.addAttribute("form", form);
        return "admin/crud";
    }
}
