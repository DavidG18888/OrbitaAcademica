package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@Controller
public class LoginController {
    private final TursoDAO dao; private final BCryptPasswordEncoder encoder;
    public LoginController(TursoDAO dao, BCryptPasswordEncoder encoder) { this.dao=dao; this.encoder=encoder; }
    @GetMapping("/") public String inicio(HttpSession session) { return session.getAttribute("usuario")!=null?"redirect:/panel":"redirect:/login"; }
    @GetMapping("/login") public String login(HttpSession session) { return session.getAttribute("usuario")!=null?"redirect:/panel":"login"; }
    @PostMapping("/login") public String ingresar(@RequestParam String usuario,@RequestParam String clave,Model model,HttpSession session) {
        try {
            List<Map<String,Object>> rows=dao.consultar("SELECT * FROM Usuarios WHERE usuario=? AND estado='ACTIVO'",usuario.trim());
            if(rows.isEmpty() || rows.get(0).get("clave")==null || !encoder.matches(clave,String.valueOf(rows.get(0).get("clave")))) {
                model.addAttribute("error","Usuario o contraseña incorrectos."); return "login";
            }
            Map<String,Object> u=rows.get(0); session.setAttribute("usuario",u.get("usuario")); session.setAttribute("nombre",u.get("nombre")); session.setAttribute("rol",u.get("rol"));
            return "redirect:/panel";
        } catch(Exception e) { model.addAttribute("error","No se pudo iniciar sesión. Verifique la conexión con la base de datos."); System.err.println("Login error: "+e.getMessage()); return "login"; }
    }
    @GetMapping("/panel") public String panel(Model model,HttpSession session) {
        if(session.getAttribute("usuario")==null) return "redirect:/login";
        model.addAttribute("nombre",session.getAttribute("nombre")); model.addAttribute("rol",session.getAttribute("rol"));
        try {
            model.addAttribute("estudiantes",count("Estudiantes")); model.addAttribute("docentes",count("Docentes")); model.addAttribute("asignaturas",count("Asignaturas")); model.addAttribute("matriculas",count("Matriculas"));
        } catch(Exception e) { model.addAttribute("estudiantes",0); model.addAttribute("docentes",0); model.addAttribute("asignaturas",0); model.addAttribute("matriculas",0); model.addAttribute("dbError",true); }
        return "panel";
    }
    private int count(String table) { return Integer.parseInt(String.valueOf(dao.consultar("SELECT COUNT(*) AS total FROM "+table).get(0).get("total"))); }
    @GetMapping("/salir") public String salir(HttpSession session) { session.invalidate(); return "redirect:/login"; }
}
