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

    public LoginController(
            TursoDAO dao,
            BCryptPasswordEncoder encoder) {

        this.dao = dao;
        this.encoder = encoder;
    }

    // Página inicial
    @GetMapping("/")
    public String inicio(HttpSession session) {

        if (session.getAttribute("usuario") != null) {
            return "redirect:/panel";
        }

        return "redirect:/login";
    }

    // Mostrar formulario de login
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // Validar usuario y contraseña
    @PostMapping("/login")
    public String ingresar(
            @RequestParam String usuario,
            @RequestParam String clave,
            Model model,
            HttpSession session) {

        try {

            String sql = "SELECT * FROM Usuarios "
                    + "WHERE usuario = ? AND estado = 'ACTIVO'";

            List<Map<String, Object>> datos =
                    dao.consultar(sql, usuario.trim());

            // Verificar si existe el usuario
            if (datos.isEmpty()) {

                model.addAttribute(
                        "error",
                        "Usuario o contraseña incorrectos."
                );

                return "login";
            }

            // Obtener datos del usuario
            Map<String, Object> u = datos.get(0);

            String claveGuardada =
                    String.valueOf(u.get("clave"));

            // Validar contraseña
            if (!encoder.matches(clave, claveGuardada)) {

                model.addAttribute(
                        "error",
                        "Usuario o contraseña incorrectos."
                );

                return "login";
            }

            // Guardar datos en sesión
            session.setAttribute(
                    "usuario",
                    u.get("usuario")
            );

            session.setAttribute(
                    "nombre",
                    u.get("nombre")
            );

            session.setAttribute(
                    "rol",
                    u.get("rol")
            );

            return "redirect:/panel";

        } catch (Exception e) {

            model.addAttribute(
                    "error",
                    "No fue posible iniciar sesión. "
                    + "Revise la conexión de la base de datos."
            );

            return "login";
        }
    }

    // Panel principal: cada rol va a su portal
    @GetMapping("/panel")
    public String panel(Model model, HttpSession session) {

        String rol = (String) session.getAttribute("rol");

        if (rol == null) {
            return "redirect:/login";
        }
        if (rol.equals("DOCENTE")) {
            return "redirect:/docente";
        }
        if (rol.equals("ESTUDIANTE")) {
            return "redirect:/estudiante";
        }

        model.addAttribute("st", dao.consultar(
                "SELECT (SELECT COUNT(*) FROM Estudiantes) AS estudiantes, "
                + "(SELECT COUNT(*) FROM Docentes) AS docentes, "
                + "(SELECT COUNT(*) FROM Asignaturas) AS asignaturas, "
                + "(SELECT COUNT(*) FROM Matriculas) AS matriculas").get(0));

        return "panel";
    }

    // Cerrar sesión
    @GetMapping("/salir")
    public String salir(HttpSession session) {

        session.invalidate();

        return "redirect:/login";
    }
}