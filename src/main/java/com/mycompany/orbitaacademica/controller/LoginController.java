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

    // Panel principal
    @GetMapping("/panel")
    public String panel(
            Model model,
            HttpSession session) {

        // Verificar sesión
        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }

        model.addAttribute(
                "nombre",
                session.getAttribute("nombre")
        );

        model.addAttribute("rol", session.getAttribute("rol"));
        try {
            model.addAttribute("totalEstudiantes", dao.consultar("SELECT COUNT(*) AS total FROM Estudiantes").get(0).get("total"));
            model.addAttribute("totalDocentes", dao.consultar("SELECT COUNT(*) AS total FROM Docentes").get(0).get("total"));
            model.addAttribute("totalAsignaturas", dao.consultar("SELECT COUNT(*) AS total FROM Asignaturas").get(0).get("total"));
            model.addAttribute("totalMatriculas", dao.consultar("SELECT COUNT(*) AS total FROM Matriculas").get(0).get("total"));
        } catch (Exception e) {
            model.addAttribute("totalEstudiantes", "0"); model.addAttribute("totalDocentes", "0"); model.addAttribute("totalAsignaturas", "0"); model.addAttribute("totalMatriculas", "0");
        }
        return "panel";
    }

    // Cerrar sesión
    @GetMapping("/salir")
    public String salir(HttpSession session) {

        session.invalidate();

        return "redirect:/login";
    }
}