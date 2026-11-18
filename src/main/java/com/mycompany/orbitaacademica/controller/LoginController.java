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

        System.out.println("========== LOGIN ==========");
        System.out.println("Usuario recibido: " + usuario);

        try {

            String sql = "SELECT * FROM Usuarios "
                    + "WHERE usuario = ? AND estado = 'ACTIVO'";

            List<Map<String, Object>> datos =
                    dao.consultar(sql, usuario.trim());

            System.out.println(
                    "Registros encontrados: " + datos.size()
            );

            // Verificar si existe el usuario
            if (datos.isEmpty()) {

                System.out.println(
                        "RESULTADO: Usuario no encontrado o inactivo"
                );

                model.addAttribute(
                        "error",
                        "Usuario o contraseña incorrectos."
                );

                return "login";
            }

            // Obtener datos
            Map<String, Object> u = datos.get(0);

            String usuarioDB =
                    String.valueOf(u.get("usuario"));

            String nombreDB =
                    String.valueOf(u.get("nombre"));

            String rolDB =
                    String.valueOf(u.get("rol"));

            String estadoDB =
                    String.valueOf(u.get("estado"));

            Object claveObjeto = u.get("clave");

            // Verificar que exista la contraseña
            if (claveObjeto == null) {

                System.out.println(
                        "RESULTADO: La contraseña almacenada es NULL"
                );

                model.addAttribute(
                        "error",
                        "Usuario o contraseña incorrectos."
                );

                return "login";
            }

            String claveGuardada = claveObjeto.toString();

            System.out.println("Usuario DB: " + usuarioDB);
            System.out.println("Rol DB: " + rolDB);
            System.out.println("Estado DB: " + estadoDB);

            System.out.println(
                    "Hash BCrypt: "
                    + claveGuardada.startsWith("$2")
            );

            // Validar contraseña
            boolean coincide = encoder.matches(
                    clave,
                    claveGuardada
            );

            System.out.println(
                    "Contraseña coincide: " + coincide
            );

            // Contraseña incorrecta
            if (!coincide) {

                System.out.println(
                        "RESULTADO: Contraseña incorrecta"
                );

                model.addAttribute(
                        "error",
                        "Usuario o contraseña incorrectos."
                );

                return "login";
            }

            // Login correcto
            System.out.println(
                    "RESULTADO: Inicio de sesión exitoso"
            );

            // Crear sesión
            session.setAttribute("usuario", usuarioDB);
            session.setAttribute("nombre", nombreDB);
            session.setAttribute("rol", rolDB);

            System.out.println(
                    "Sesión creada correctamente"
            );

            System.out.println("==========================");

            return "redirect:/panel";

        } catch (Exception e) {

            System.err.println(
                    "ERROR DURANTE EL LOGIN:"
            );

            e.printStackTrace();

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

        if (session.getAttribute("usuario") == null) {
            return "redirect:/login";
        }

        model.addAttribute(
                "nombre",
                session.getAttribute("nombre")
        );

        model.addAttribute(
                "rol",
                session.getAttribute("rol")
        );

        return "panel";
    }

    // Cerrar sesión
    @GetMapping("/salir")
    public String salir(HttpSession session) {

        session.invalidate();

        return "redirect:/login";
    }
}