package com.mycompany.orbitaacademica.controller;
import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.stereotype.Controller;import org.springframework.ui.Model;import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;import java.util.*;
@Controller
public class ModuloController {
 private final TursoDAO dao;
 public ModuloController(TursoDAO d){dao=d;}
 private record Mod(String title,String table,String[] cols,String[] labels){}
 private final Map<String,Mod> mods=Map.of(
 "usuarios",new Mod("Usuarios","Usuarios",new String[]{"usuario","nombre","correo","rol","estado"},new String[]{"Usuario","Nombre","Correo","Rol","Estado"}),
 "estudiantes",new Mod("Estudiantes","Estudiantes",new String[]{"codigo","nombre","apellido","dni","carrera","correo","telefono","estado"},new String[]{"Código","Nombre","Apellido","DNI","Carrera","Correo","Teléfono","Estado"}),
 "docentes",new Mod("Docentes","Docentes",new String[]{"codigo","nombre","apellido","especialidad","correo","telefono","estado"},new String[]{"Código","Nombre","Apellido","Especialidad","Correo","Teléfono","Estado"}),
 "asignaturas",new Mod("Asignaturas","Asignaturas",new String[]{"codigo","nombre","carrera","creditos","estado"},new String[]{"Código","Nombre","Carrera","Créditos","Estado"}),
 "periodos",new Mod("Periodos académicos","Periodos",new String[]{"nombre","fecha_inicio","fecha_fin","estado"},new String[]{"Nombre","Inicio","Fin","Estado"}),
 "matriculas",new Mod("Matrículas","Matriculas",new String[]{"estudiante_id","asignatura_id","periodo_id","seccion","fecha","estado"},new String[]{"ID estudiante","ID asignatura","ID periodo","Sección","Fecha","Estado"}),
 "calificaciones",new Mod("Calificaciones","Calificaciones",new String[]{"matricula_id","parcial1","parcial2","parcial3","final","observacion"},new String[]{"Matrícula","Parcial 1","Parcial 2","Parcial 3","Final","Observación"}),
 "asistencia",new Mod("Asistencia","Asistencia",new String[]{"matricula_id","fecha","estado","observacion"},new String[]{"Matrícula","Fecha","Estado","Observación"}),
 "horarios",new Mod("Horarios","Horarios",new String[]{"asignatura_id","docente_id","seccion","dia","hora_inicio","hora_fin","aula"},new String[]{"Asignatura","Docente","Sección","Día","Inicio","Fin","Aula"})
 );
 @GetMapping("/modulos/{mod}") public String lista(@PathVariable String mod,Model model,HttpSession s){
  if(s.getAttribute("usuario")==null)return "redirect:/login";
  Mod m=mods.get(mod);if(m==null)return "redirect:/panel";
  List<Map<String,Object>> rows=dao.consultar("SELECT * FROM "+m.table+" ORDER BY id DESC");
  model.addAttribute("mod",mod);model.addAttribute("title",m.title);model.addAttribute("rows",rows);model.addAttribute("cols",m.cols);model.addAttribute("labels",m.labels);model.addAttribute("nombre",s.getAttribute("nombre"));model.addAttribute("rol",s.getAttribute("rol"));
  return "modulo";
 }
 @PostMapping("/modulos/{mod}/guardar") public String guardar(@PathVariable String mod,@RequestParam Map<String,String> form,HttpSession s){
  if(s.getAttribute("usuario")==null)return "redirect:/login"; Mod m=mods.get(mod);if(m==null)return "redirect:/panel";
  String id=form.get("id");List<String> cols=Arrays.asList(m.cols);StringBuilder sql;
  if(id!=null&&!id.isBlank()){sql=new StringBuilder("UPDATE "+m.table+" SET ");for(int i=0;i<cols.size();i++){if(i>0)sql.append(",");sql.append(cols.get(i)).append("=?");}sql.append(" WHERE id=?");List<Object> args=new ArrayList<>();for(String c:cols)args.add(form.get(c));args.add(id);dao.ejecutarSQL(sql.toString(),args.toArray());}
  else {sql=new StringBuilder("INSERT INTO "+m.table+" (");sql.append(String.join(",",cols));sql.append(") VALUES (");sql.append(String.join(",",Collections.nCopies(cols.size(),"?")));sql.append(")");List<Object> args=new ArrayList<>();for(String c:cols)args.add(form.get(c));dao.ejecutarSQL(sql.toString(),args.toArray());}
  return "redirect:/modulos/"+mod;
 }
 @PostMapping("/modulos/{mod}/eliminar") public String eliminar(@PathVariable String mod,@RequestParam String id,HttpSession s){if(s.getAttribute("usuario")==null)return "redirect:/login";Mod m=mods.get(mod);if(m!=null)dao.ejecutarSQL("DELETE FROM "+m.table+" WHERE id=?",id);return "redirect:/modulos/"+mod;}
 @GetMapping("/portal/{rol}") public String portal(@PathVariable String rol,Model model,HttpSession s){if(s.getAttribute("usuario")==null)return "redirect:/login";model.addAttribute("rol",rol);model.addAttribute("nombre",s.getAttribute("nombre"));model.addAttribute("usuario",s.getAttribute("usuario"));return "portal";}
}
