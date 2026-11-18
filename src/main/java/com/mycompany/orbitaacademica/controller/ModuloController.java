package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;

@Controller
@RequestMapping("/modulos")
public class ModuloController {
    private final TursoDAO dao;
    private final BCryptPasswordEncoder encoder;

    public ModuloController(TursoDAO dao, BCryptPasswordEncoder encoder) {
        this.dao = dao;
        this.encoder = encoder;
    }

    private static class Campo {
        String nombre, etiqueta, tipo;
        Campo(String n, String e, String t) { nombre=n; etiqueta=e; tipo=t; }
    }
    private static class Modulo {
        String tabla, titulo, singular;
        List<Campo> campos;
        Modulo(String t, String titulo, String singular, Campo... c) {
            tabla=t; this.titulo=titulo; this.singular=singular; campos=Arrays.asList(c);
        }
    }
    private final Map<String, Modulo> modulos = crearModulos();
    private Map<String, Modulo> crearModulos() {
        Map<String,Modulo> m=new LinkedHashMap<>();
        m.put("estudiantes",new Modulo("Estudiantes","Estudiantes","estudiante",
            new Campo("codigo","Código","text"),new Campo("nombre","Nombre","text"),new Campo("apellido","Apellido","text"),new Campo("carrera","Carrera","text"),new Campo("usuario_id","ID usuario","number"),new Campo("estado","Estado","select")));
        m.put("docentes",new Modulo("Docentes","Docentes","docente",
            new Campo("codigo","Código","text"),new Campo("nombre","Nombre","text"),new Campo("apellido","Apellido","text"),new Campo("especialidad","Especialidad","text"),new Campo("usuario_id","ID usuario","number"),new Campo("estado","Estado","select")));
        m.put("carreras",new Modulo("Carreras","Carreras","carrera",
            new Campo("codigo","Código","text"),new Campo("nombre","Nombre","text"),new Campo("descripcion","Descripción","textarea"),new Campo("duracion","Duración (años)","number"),new Campo("estado","Estado","select")));
        m.put("asignaturas",new Modulo("Asignaturas","Asignaturas","asignatura",
            new Campo("codigo","Código","text"),new Campo("nombre","Nombre","text"),new Campo("carrera_id","ID carrera","number"),new Campo("docente_id","ID docente","number"),new Campo("creditos","Créditos","number"),new Campo("estado","Estado","select")));
        m.put("periodos",new Modulo("Periodos","Períodos académicos","período",
            new Campo("nombre","Nombre","text"),new Campo("fecha_inicio","Fecha inicio","date"),new Campo("fecha_fin","Fecha fin","date"),new Campo("estado","Estado","select")));
        m.put("matriculas",new Modulo("Matriculas","Matrículas","matrícula",
            new Campo("estudiante_id","ID estudiante","number"),new Campo("asignatura_id","ID asignatura","number"),new Campo("periodo_id","ID período","number"),new Campo("fecha","Fecha","date"),new Campo("estado","Estado","select")));
        m.put("horarios",new Modulo("Horarios","Horarios","horario",
            new Campo("asignatura_id","ID asignatura","number"),new Campo("docente_id","ID docente","number"),new Campo("periodo_id","ID período","number"),new Campo("dia","Día","select"),new Campo("hora_inicio","Hora inicio","time"),new Campo("hora_fin","Hora fin","time"),new Campo("aula","Aula","text")));
        m.put("calificaciones",new Modulo("Calificaciones","Calificaciones","calificación",
            new Campo("estudiante_id","ID estudiante","number"),new Campo("asignatura_id","ID asignatura","number"),new Campo("periodo_id","ID período","number"),new Campo("nota","Nota","number"),new Campo("observacion","Observación","textarea")));
        m.put("asistencias",new Modulo("Asistencias","Asistencia","registro de asistencia",
            new Campo("estudiante_id","ID estudiante","number"),new Campo("asignatura_id","ID asignatura","number"),new Campo("fecha","Fecha","date"),new Campo("estado","Estado asistencia","select"),new Campo("observacion","Observación","textarea")));
        m.put("usuarios",new Modulo("Usuarios","Usuarios del sistema","usuario",
            new Campo("usuario","Usuario","text"),new Campo("nombre","Nombre completo","text"),new Campo("correo","Correo","email"),new Campo("clave","Contraseña","password"),new Campo("rol","Rol","select"),new Campo("estado","Estado","select")));
        return m;
    }
    private Modulo obtener(String slug) {
        Modulo m=modulos.get(slug);
        if(m==null) throw new IllegalArgumentException("Módulo no válido");
        return m;
    }
    private boolean autenticado(HttpSession s) { return s.getAttribute("usuario")!=null; }
    private boolean admin(HttpSession s) { return "ADMIN".equals(String.valueOf(s.getAttribute("rol"))); }
    private String denegado(Model model) { model.addAttribute("error","No tiene permisos para administrar usuarios."); return "login"; }

    @GetMapping("/{slug}")
    public String listar(@PathVariable String slug, Model model, HttpSession session) {
        if(!autenticado(session)) return "redirect:/login";
        if("usuarios".equals(slug)&&!admin(session)) return denegado(model);
        Modulo m=obtener(slug);
        List<Map<String,Object>> filas=dao.consultar("SELECT * FROM "+m.tabla+" ORDER BY id DESC");
        model.addAttribute("slug",slug); model.addAttribute("titulo",m.titulo); model.addAttribute("filas",filas); model.addAttribute("campos",m.campos);
        model.addAttribute("nombre",session.getAttribute("nombre")); model.addAttribute("rol",session.getAttribute("rol"));
        return "modulo-lista";
    }
    @GetMapping("/{slug}/nuevo")
    public String nuevo(@PathVariable String slug, Model model, HttpSession session) {
        if(!autenticado(session)) return "redirect:/login";
        if("usuarios".equals(slug)&&!admin(session)) return denegado(model);
        Modulo m=obtener(slug); model.addAttribute("slug",slug); model.addAttribute("titulo",m.titulo); model.addAttribute("singular",m.singular); model.addAttribute("campos",m.campos); model.addAttribute("registro",new HashMap<String,Object>()); model.addAttribute("modo","nuevo");
        return "modulo-form";
    }
    @GetMapping("/{slug}/editar/{id}")
    public String editar(@PathVariable String slug,@PathVariable int id,Model model,HttpSession session) {
        if(!autenticado(session)) return "redirect:/login";
        if("usuarios".equals(slug)&&!admin(session)) return denegado(model);
        Modulo m=obtener(slug); List<Map<String,Object>> r=dao.consultar("SELECT * FROM "+m.tabla+" WHERE id=?",id);
        if(r.isEmpty()) return "redirect:/modulos/"+slug;
        model.addAttribute("slug",slug); model.addAttribute("titulo",m.titulo); model.addAttribute("singular",m.singular); model.addAttribute("campos",m.campos); model.addAttribute("registro",r.get(0)); model.addAttribute("modo","editar");
        return "modulo-form";
    }
    @PostMapping("/{slug}/guardar")
    public String guardar(@PathVariable String slug,@RequestParam Map<String,String> params,HttpSession session) {
        if(!autenticado(session)) return "redirect:/login";
        if("usuarios".equals(slug)&&!admin(session)) return "redirect:/panel";
        Modulo m=obtener(slug); String id=params.get("id"); List<String> cols=new ArrayList<>(); List<Object> vals=new ArrayList<>();
        for(Campo c:m.campos){String v=params.get(c.nombre); if("clave".equals(c.nombre)&& (v==null||v.isBlank())) continue; if("clave".equals(c.nombre)) v=encoder.encode(v); cols.add(c.nombre); vals.add((v==null||v.isBlank())?null:v); }
        try {
            if(id==null||id.isBlank()) {
                String sql="INSERT INTO "+m.tabla+" ("+String.join(",",cols)+") VALUES ("+String.join(",",Collections.nCopies(cols.size(),"?"))+")";
                dao.ejecutarSQL(sql,vals.toArray());
            } else {
                List<String> sets=new ArrayList<>(); for(String c:cols) sets.add(c+"=?"); vals.add(Integer.parseInt(id));
                dao.ejecutarSQL("UPDATE "+m.tabla+" SET "+String.join(",",sets)+" WHERE id=?",vals.toArray());
            }
        } catch(Exception ex) { return "redirect:/modulos/"+slug+"?error=guardar"; }
        return "redirect:/modulos/"+slug+"?ok=1";
    }
    @PostMapping("/{slug}/eliminar/{id}")
    public String eliminar(@PathVariable String slug,@PathVariable int id,HttpSession session) {
        if(!autenticado(session)) return "redirect:/login";
        if("usuarios".equals(slug)&&!admin(session)) return "redirect:/panel";
        Modulo m=obtener(slug);
        if("usuarios".equals(slug)) { String actual=String.valueOf(session.getAttribute("usuario")); List<Map<String,Object>> u=dao.consultar("SELECT usuario FROM Usuarios WHERE id=?",id); if(!u.isEmpty()&&actual.equals(String.valueOf(u.get(0).get("usuario")))) return "redirect:/modulos/usuarios?error=propio"; }
        try { dao.ejecutarSQL("DELETE FROM "+m.tabla+" WHERE id=?",id); } catch(Exception ex) { return "redirect:/modulos/"+slug+"?error=eliminar"; }
        return "redirect:/modulos/"+slug+"?ok=eliminado";
    }
}
