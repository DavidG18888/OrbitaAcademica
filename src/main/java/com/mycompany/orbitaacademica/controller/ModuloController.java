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
    private final TursoDAO dao; private final BCryptPasswordEncoder encoder;
    public ModuloController(TursoDAO dao,BCryptPasswordEncoder encoder){this.dao=dao;this.encoder=encoder;}

    static class Campo { final String name,label,type; Campo(String n,String l,String t){name=n;label=l;type=t;} }
    static class Modulo { final String table,title,singular; final List<Campo> fields; Modulo(String t,String title,String singular,Campo...f){table=t;this.title=title;this.singular=singular;fields=List.of(f);} }
    private final Map<String,Modulo> modules=build();
    private Map<String,Modulo> build(){Map<String,Modulo> m=new LinkedHashMap<>();
      m.put("estudiantes",new Modulo("Estudiantes","Estudiantes","estudiante",new Campo("codigo","Código","text"),new Campo("nombre","Nombre","text"),new Campo("apellido","Apellido","text"),new Campo("carrera","Carrera","text"),new Campo("telefono","Teléfono","text"),new Campo("correo","Correo","email"),new Campo("estado","Estado","select")));
      m.put("docentes",new Modulo("Docentes","Docentes","docente",new Campo("codigo","Código","text"),new Campo("nombre","Nombre","text"),new Campo("apellido","Apellido","text"),new Campo("especialidad","Especialidad","text"),new Campo("telefono","Teléfono","text"),new Campo("correo","Correo","email"),new Campo("estado","Estado","select")));
      m.put("carreras",new Modulo("Carreras","Carreras","carrera",new Campo("codigo","Código","text"),new Campo("nombre","Nombre","text"),new Campo("descripcion","Descripción","textarea"),new Campo("duracion","Duración (años)","number"),new Campo("estado","Estado","select")));
      m.put("asignaturas",new Modulo("Asignaturas","Asignaturas","asignatura",new Campo("codigo","Código","text"),new Campo("nombre","Nombre","text"),new Campo("carrera_id","ID de carrera","number"),new Campo("docente_id","ID de docente","number"),new Campo("creditos","Créditos","number"),new Campo("estado","Estado","select")));
      m.put("periodos",new Modulo("Periodos","Períodos académicos","período",new Campo("nombre","Nombre","text"),new Campo("fecha_inicio","Fecha de inicio","date"),new Campo("fecha_fin","Fecha de finalización","date"),new Campo("estado","Estado","select")));
      m.put("matriculas",new Modulo("Matriculas","Matrículas","matrícula",new Campo("estudiante_id","ID estudiante","number"),new Campo("asignatura_id","ID asignatura","number"),new Campo("periodo_id","ID período","number"),new Campo("fecha","Fecha","date"),new Campo("estado","Estado","select")));
      m.put("horarios",new Modulo("Horarios","Horarios","horario",new Campo("asignatura_id","ID asignatura","number"),new Campo("docente_id","ID docente","number"),new Campo("periodo_id","ID período","number"),new Campo("dia","Día","select"),new Campo("hora_inicio","Hora de inicio","time"),new Campo("hora_fin","Hora de finalización","time"),new Campo("aula","Aula","text")));
      m.put("calificaciones",new Modulo("Calificaciones","Calificaciones","calificación",new Campo("estudiante_id","ID estudiante","number"),new Campo("asignatura_id","ID asignatura","number"),new Campo("periodo_id","ID período","number"),new Campo("nota","Nota","number"),new Campo("observacion","Observación","textarea")));
      m.put("asistencias",new Modulo("Asistencias","Asistencia","registro de asistencia",new Campo("estudiante_id","ID estudiante","number"),new Campo("asignatura_id","ID asignatura","number"),new Campo("fecha","Fecha","date"),new Campo("estado","Estado","select"),new Campo("observacion","Observación","textarea")));
      m.put("usuarios",new Modulo("Usuarios","Usuarios","usuario",new Campo("usuario","Usuario","text"),new Campo("nombre","Nombre completo","text"),new Campo("correo","Correo","email"),new Campo("clave","Contraseña","password"),new Campo("rol","Rol","select"),new Campo("estado","Estado","select")));
      return m;
    }
    private Modulo get(String slug){Modulo m=modules.get(slug);if(m==null)throw new IllegalArgumentException("Módulo inexistente");return m;}
    private boolean auth(HttpSession s){return s.getAttribute("usuario")!=null;}
    private boolean isAdmin(HttpSession s){return "ADMIN".equalsIgnoreCase(String.valueOf(s.getAttribute("rol")));}
    private String menu(String slug){return slug;}
    private void common(Model model,HttpSession session,Modulo m,String slug){model.addAttribute("slug",slug);model.addAttribute("titulo",m.title);model.addAttribute("singular",m.singular);model.addAttribute("campos",m.fields);model.addAttribute("nombre",session.getAttribute("nombre"));model.addAttribute("rol",session.getAttribute("rol"));}
    @GetMapping("/{slug}") public String listar(@PathVariable String slug,Model model,HttpSession session){if(!auth(session))return "redirect:/login";if(slug.equals("usuarios")&&!isAdmin(session))return "redirect:/panel";Modulo m=get(slug);List<Map<String,Object>> rows=dao.consultar("SELECT * FROM "+m.table+" ORDER BY id DESC");common(model,session,m,slug);model.addAttribute("filas",rows);return "modulo-lista";}
    @GetMapping("/{slug}/nuevo") public String nuevo(@PathVariable String slug,Model model,HttpSession session){if(!auth(session))return "redirect:/login";if(slug.equals("usuarios")&&!isAdmin(session))return "redirect:/panel";Modulo m=get(slug);common(model,session,m,slug);model.addAttribute("registro",Collections.emptyMap());model.addAttribute("modo","nuevo");return "modulo-form";}
    @GetMapping("/{slug}/editar/{id}") public String editar(@PathVariable String slug,@PathVariable int id,Model model,HttpSession session){if(!auth(session))return "redirect:/login";if(slug.equals("usuarios")&&!isAdmin(session))return "redirect:/panel";Modulo m=get(slug);List<Map<String,Object>> rows=dao.consultar("SELECT * FROM "+m.table+" WHERE id=?",id);if(rows.isEmpty())return "redirect:/modulos/"+slug;common(model,session,m,slug);model.addAttribute("registro",rows.get(0));model.addAttribute("modo","editar");return "modulo-form";}
    @PostMapping("/{slug}/guardar") public String guardar(@PathVariable String slug,@RequestParam Map<String,String> p,HttpSession session){if(!auth(session))return "redirect:/login";if(slug.equals("usuarios")&&!isAdmin(session))return "redirect:/panel";Modulo m=get(slug);List<String> columns=new ArrayList<>();List<Object> values=new ArrayList<>();for(Campo f:m.fields){String v=p.get(f.name);if(f.name.equals("clave")&&(v==null||v.isBlank()))continue;if(f.name.equals("clave")&&v!=null&&!v.isBlank())v=encoder.encode(v);columns.add(f.name);values.add(v==null||v.isBlank()?null:v.trim());}
      try{String id=p.get("id");if(id==null||id.isBlank()){String sql="INSERT INTO "+m.table+" ("+String.join(",",columns)+") VALUES ("+String.join(",",Collections.nCopies(columns.size(),"?"))+")";dao.ejecutarSQL(sql,values.toArray());}else{List<String> sets=new ArrayList<>();for(String c:columns)sets.add(c+"=?");values.add(Integer.parseInt(id));dao.ejecutarSQL("UPDATE "+m.table+" SET "+String.join(",",sets)+" WHERE id=?",values.toArray());}}
      catch(Exception e){System.err.println("Guardar "+slug+": "+e.getMessage());return "redirect:/modulos/"+slug+"?error=guardar";}return "redirect:/modulos/"+slug+"?ok=1";}
    @PostMapping("/{slug}/eliminar/{id}") public String eliminar(@PathVariable String slug,@PathVariable int id,HttpSession session){if(!auth(session))return "redirect:/login";if(slug.equals("usuarios")&&!isAdmin(session))return "redirect:/panel";Modulo m=get(slug);try{if(slug.equals("usuarios")){List<Map<String,Object>> target=dao.consultar("SELECT usuario FROM Usuarios WHERE id=?",id);if(!target.isEmpty()&&String.valueOf(session.getAttribute("usuario")).equals(String.valueOf(target.get(0).get("usuario"))))return "redirect:/modulos/usuarios?error=propio";}dao.ejecutarSQL("DELETE FROM "+m.table+" WHERE id=?",id);}catch(Exception e){System.err.println("Eliminar "+slug+": "+e.getMessage());return "redirect:/modulos/"+slug+"?error=eliminar";}return "redirect:/modulos/"+slug+"?ok=eliminado";}
}
