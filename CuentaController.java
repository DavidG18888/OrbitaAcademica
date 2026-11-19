package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

// Datos personales y cambio de contraseña (docentes y estudiantes)
@Controller
public class CuentaController {

    private final TursoDAO dao;
    private final BCryptPasswordEncoder encoder;

    public CuentaController(TursoDAO dao, BCryptPasswordEncoder encoder) {
        this.dao = dao;
        this.encoder = encoder;
    }

    private static boolean docente(HttpSession s) { return "DOCENTE".equals(s.getAttribute("rol")); }

    @GetMapping({"/docente/cuenta", "/estudiante/cuenta"})
    public String ver(Model m, HttpSession s) {
        boolean d = docente(s);
        List<Map<String, Object>> r = dao.consultar(d
                ? "SELECT t.codigo, t.nombre, t.apellido, t.especialidad, t.correo, t.telefono, t.estado FROM Docentes t JOIN Usuarios u ON u.id = t.usuario_id WHERE u.usuario = ?"
                : "SELECT t.codigo, t.nombre, t.apellido, c.nombre AS carrera, t.correo, t.telefono, t.estado FROM Estudiantes t JOIN Usuarios u ON u.id = t.usuario_id LEFT JOIN Carreras c ON c.id = t.carrera_id WHERE u.usuario = ?",
                s.getAttribute("usuario"));
        Map<String, Object> f = r.isEmpty() ? Map.of() : r.get(0);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("Código", f.get("codigo"));
        datos.put("Nombre completo", f.get("nombre") + " " + f.get("apellido"));
        datos.put(d ? "Especialidad" : "Carrera", d ? f.get("especialidad") : f.get("carrera"));
        datos.put("Estado", f.get("estado"));
        m.addAttribute("datos", datos);
        m.addAttribute("correo", f.get("correo"));
        m.addAttribute("telefono", f.get("telefono"));
        m.addAttribute("base", d ? "/docente" : "/estudiante");
        return "cuenta";
    }

    @PostMapping({"/docente/cuenta/contacto", "/estudiante/cuenta/contacto"})
    public String contacto(@RequestParam String correo, @RequestParam String telefono, HttpSession s, RedirectAttributes ra) {
        try {
            dao.ejecutarSQL("UPDATE " + (docente(s) ? "Docentes" : "Estudiantes")
                    + " SET correo = ?, telefono = ? WHERE usuario_id = (SELECT id FROM Usuarios WHERE usuario = ?)",
                    correo.isBlank() ? null : correo.trim(), telefono.isBlank() ? null : telefono.trim(), s.getAttribute("usuario"));
            ra.addFlashAttribute("ok", "Datos de contacto actualizados.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudieron guardar los datos.");
        }
        return "redirect:" + (docente(s) ? "/docente" : "/estudiante") + "/cuenta";
    }

    @PostMapping({"/docente/cuenta/clave", "/estudiante/cuenta/clave"})
    public String clave(@RequestParam String actual, @RequestParam String nueva, @RequestParam String confirmar, HttpSession s, RedirectAttributes ra) {
        String destino = "redirect:" + (docente(s) ? "/docente" : "/estudiante") + "/cuenta";
        List<Map<String, Object>> u = dao.consultar("SELECT clave FROM Usuarios WHERE usuario = ?", s.getAttribute("usuario"));
        if (u.isEmpty() || !encoder.matches(actual, String.valueOf(u.get(0).get("clave")))) {
            ra.addFlashAttribute("error", "La contraseña actual no es correcta.");
        } else if (nueva.length() < 8) {
            ra.addFlashAttribute("error", "La nueva contraseña debe tener al menos 8 caracteres.");
        } else if (!nueva.equals(confirmar)) {
            ra.addFlashAttribute("error", "La confirmación no coincide.");
        } else {
            dao.ejecutarSQL("UPDATE Usuarios SET clave = ? WHERE usuario = ?", encoder.encode(nueva), s.getAttribute("usuario"));
            ra.addFlashAttribute("ok", "Contraseña actualizada.");
        }
        return destino;
    }
}
