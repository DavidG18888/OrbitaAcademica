package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@Controller
public class LoginController {

    private final TursoDAO dao;
    private final BCryptPasswordEncoder encoder;

    public LoginController(TursoDAO dao, BCryptPasswordEncoder encoder) {
        this.dao = dao;
        this.encoder = encoder;
    }

    @GetMapping("/")
    public String inicio(HttpSession session) {
        if (session.getAttribute("usuario") != null) {
            return "redirect:/panel";
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login")
    public String ingresar(@RequestParam String usuario, @RequestParam String clave, Model model, HttpSession session) {
        try {
            String sql = "SELECT * FROM Usuarios WHERE usuario = ? AND estado = 'ACTIVO'";
            List<Map<String, Object>> datos = dao.consultar(sql, usuario.trim());
            if (datos.isEmpty()) {
                model.addAttribute("error", "Usuario o contraseña incorrectos.");
                return "login";
            }
            Map<String, Object> u = datos.get(0);
            String claveGuardada = String.valueOf(u.get("clave"));
            if (!encoder.matches(clave, claveGuardada)) {
                model.addAttribute("error", "Usuario o contraseña incorrectos.");
                return "login";
            }
            session.setAttribute("usuario", u.get("usuario"));
            session.setAttribute("nombre", u.get("nombre"));
            session.setAttribute("rol", u.get("rol"));
            return "redirect:/panel";
        } catch (Exception e) {
            model.addAttribute("error", "No fue posible iniciar sesión. Revise la conexión de la base de datos.");
            return "login";
        }
    }

    @GetMapping("/panel")
    public String panel(Model model, HttpSession session) {
        String rol = (String) session.getAttribute("rol");
        if (rol == null) return "redirect:/login";
        if (rol.equals("DOCENTE")) return "redirect:/docente";
        if (rol.equals("ESTUDIANTE")) return "redirect:/estudiante";

        model.addAttribute("st", dao.consultar(
                "SELECT (SELECT COUNT(*) FROM Estudiantes) AS estudiantes, "
                + "(SELECT COUNT(*) FROM Docentes) AS docentes, "
                + "(SELECT COUNT(*) FROM Asignaturas) AS asignaturas, "
                + "(SELECT COUNT(*) FROM Matriculas WHERE estado = 'ACTIVA') AS matriculas, "
                + "(SELECT COUNT(*) FROM DocumentosEstudiante WHERE estado IN ('PENDIENTE','EN_REVISION')) AS docs_pendientes, "
                + "(SELECT COUNT(*) FROM Tickets WHERE estado IN ('PENDIENTE','EN_PROCESO')) AS tickets_abiertos").get(0));

        model.addAttribute("eventos", dao.consultar(
                "SELECT * FROM EventosAcademicos WHERE COALESCE(estado,'ACTIVO') = 'ACTIVO' ORDER BY fecha_inicio ASC LIMIT 6"));

        return "panel";
    }

    @GetMapping("/salir")
    public String salir(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
