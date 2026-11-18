package com.mycompany.orbitaacademica.controller;
import com.mycompany.orbitaacademica.dao.TursoDAO;import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;import org.springframework.stereotype.Controller;import org.springframework.ui.Model;import org.springframework.web.bind.annotation.*;import jakarta.servlet.http.HttpSession;import java.util.*;
@Controller
public class LoginController {
 private final TursoDAO dao;private final BCryptPasswordEncoder encoder;
 public LoginController(TursoDAO d,BCryptPasswordEncoder e){dao=d;encoder=e;}
 @GetMapping("/") public String inicio(HttpSession s){return s.getAttribute("usuario")==null?"redirect:/login":"redirect:/panel";}
 @GetMapping("/login") public String login(){return "login";}
 @PostMapping("/login") public String ingresar(@RequestParam String usuario,@RequestParam String clave,Model model,HttpSession s){try{
  List<Map<String,Object>> rs=dao.consultar("SELECT * FROM Usuarios WHERE usuario=? AND estado='ACTIVO'",usuario.trim());
  if(rs.isEmpty()||!encoder.matches(clave,String.valueOf(rs.get(0).get("clave")))){model.addAttribute("error","Usuario o contraseña incorrectos.");return "login";}
  Map<String,Object> u=rs.get(0);s.setAttribute("usuario",u.get("usuario"));s.setAttribute("nombre",u.get("nombre"));s.setAttribute("rol",u.get("rol"));return "redirect:/panel";
 }catch(Exception e){model.addAttribute("error","Error de conexión con la base de datos: "+e.getMessage());return "login";}}
 @GetMapping("/panel") public String panel(Model m,HttpSession s){if(s.getAttribute("usuario")==null)return "redirect:/login";m.addAttribute("nombre",s.getAttribute("nombre"));m.addAttribute("rol",s.getAttribute("rol"));return "panel";}
 @GetMapping("/salir") public String salir(HttpSession s){s.invalidate();return "redirect:/login";}
}
