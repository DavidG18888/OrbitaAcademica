package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller
@RequestMapping("/estudiante/oferta")
public class OfertaController {
    private final TursoDAO dao;
    public OfertaController(TursoDAO dao){this.dao=dao;}

    @GetMapping
    public String oferta(Model m,HttpSession s){
        String usuario=String.valueOf(s.getAttribute("usuario"));
        List<Map<String,Object>> estudiante=dao.consultar("SELECT id FROM Estudiantes WHERE usuario_id=(SELECT id FROM Usuarios WHERE usuario=?)",usuario);
        if(estudiante.isEmpty()) return "redirect:/estudiante";
        long eid=Long.parseLong(estudiante.get(0).get("id").toString());
        List<Map<String,Object>> lista=dao.consultar(
            "SELECT sc.id,sc.codigo AS seccion,a.codigo AS codigo_asignatura,a.nombre AS asignatura,p.nombre AS periodo,"+
            "d.nombre || ' ' || d.apellido AS docente,sc.cupo,"+
            "(SELECT COUNT(*) FROM Matriculas mm WHERE mm.seccion_id=sc.id AND mm.estado='ACTIVA') AS inscritos,"+
            "COALESCE((SELECT GROUP_CONCAT(h.dia_semana || ' ' || h.hora_inicio || '-' || h.hora_fin, ' · ') FROM HorariosSeccion h WHERE h.seccion_id=sc.id AND h.tipo='RECURRENTE'),"+
            "(SELECT GROUP_CONCAT(h.fecha || ' ' || h.hora_inicio || '-' || h.hora_fin, ' · ') FROM HorariosSeccion h WHERE h.seccion_id=sc.id)) AS horario,"+
            "CASE WHEN EXISTS(SELECT 1 FROM Matriculas x WHERE x.estudiante_id=? AND x.seccion_id=sc.id AND x.estado='ACTIVA') THEN 1 ELSE 0 END AS ya_matriculado "+
            "FROM Secciones sc JOIN Asignaturas a ON a.id=sc.asignatura_id LEFT JOIN Periodos p ON p.id=sc.periodo_id LEFT JOIN Docentes d ON d.id=sc.docente_id "+
            "WHERE sc.estado='ACTIVA' AND a.estado='ACTIVO' ORDER BY a.nombre,sc.codigo",eid);
        m.addAttribute("oferta",lista);
        return "estudiante/oferta";
    }

    @PostMapping("/{id}/matricular")
    public String matricular(@PathVariable long id,HttpSession s,RedirectAttributes ra){
        try{
            String usuario=String.valueOf(s.getAttribute("usuario"));
            List<Map<String,Object>> est=dao.consultar("SELECT id FROM Estudiantes WHERE usuario_id=(SELECT id FROM Usuarios WHERE usuario=?)",usuario);
            if(est.isEmpty()) throw new IllegalArgumentException("No se encontró el expediente del estudiante.");
            long eid=Long.parseLong(est.get(0).get("id").toString());
            List<Map<String,Object>> sec=dao.consultar("SELECT id,cupo,estado,periodo_id FROM Secciones WHERE id=?",id);
            if(sec.isEmpty() || !"ACTIVA".equalsIgnoreCase(String.valueOf(sec.get(0).get("estado")))) throw new IllegalArgumentException("La sección no está activa.");
            if(!dao.consultar("SELECT id FROM Matriculas WHERE estudiante_id=? AND seccion_id=?",eid,id).isEmpty()) throw new IllegalArgumentException("Ya existe una matrícula para esta sección.");
            double cupo=Double.parseDouble(String.valueOf(sec.get(0).get("cupo")));
            double inscritos=Double.parseDouble(String.valueOf(dao.consultar("SELECT COUNT(*) AS n FROM Matriculas WHERE seccion_id=? AND estado='ACTIVA'",id).get(0).get("n")));
            if(inscritos>=cupo) throw new IllegalArgumentException("La sección ya no tiene cupos disponibles.");
            dao.ejecutarSQL("INSERT INTO Matriculas(estudiante_id,seccion_id,estado) VALUES(?,?,'ACTIVA')",eid,id);
            ra.addFlashAttribute("ok","Matrícula realizada correctamente.");
        }catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}
        return "redirect:/estudiante/oferta";
    }
}
