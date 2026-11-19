package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalTime;
import java.util.*;

@Controller
@RequestMapping("/admin/secciones")
public class SeccionController {
    private final TursoDAO dao;
    public SeccionController(TursoDAO dao) { this.dao = dao; }

    @GetMapping
    public String listar(Model m) {
        m.addAttribute("filas", dao.consultar(
            "SELECT s.id,s.codigo,a.nombre AS asignatura,d.nombre || ' ' || d.apellido AS docente,p.nombre AS periodo,s.cupo,s.estado," +
            "(SELECT COUNT(*) FROM HorariosSeccion h WHERE h.seccion_id=s.id) AS bloques " +
            "FROM Secciones s LEFT JOIN Asignaturas a ON a.id=s.asignatura_id LEFT JOIN Docentes d ON d.id=s.docente_id " +
            "LEFT JOIN Periodos p ON p.id=s.periodo_id ORDER BY s.id DESC"));
        m.addAttribute("asignaturas", dao.consultar("SELECT id,codigo || ' · ' || nombre AS nombre FROM Asignaturas WHERE estado='ACTIVO' ORDER BY codigo"));
        m.addAttribute("docentes", dao.consultar("SELECT id,nombre || ' ' || apellido AS nombre FROM Docentes WHERE estado='ACTIVO' ORDER BY apellido"));
        m.addAttribute("periodos", dao.consultar("SELECT id,nombre FROM Periodos ORDER BY id DESC"));
        m.addAttribute("slug","secciones");
        return "admin/secciones";
    }

    @PostMapping("/guardar")
    public String guardar(@RequestParam Map<String,String> p,
                          @RequestParam(value="bloque",required=false) List<String> bloques,
                          RedirectAttributes ra) {
        try {
            String codigo=req(p,"codigo");
            long asignatura=Long.parseLong(req(p,"asignatura_id"));
            long docente=Long.parseLong(req(p,"docente_id"));
            long periodo=Long.parseLong(req(p,"periodo_id"));
            int cupo=Integer.parseInt(req(p,"cupo"));
            if(cupo<1) throw new IllegalArgumentException("El cupo debe ser mayor que cero.");
            if(bloques==null || bloques.isEmpty()) throw new IllegalArgumentException("Agregue al menos un bloque de horario.");
            for(String b:bloques) validarBloque(b);
            dao.ejecutarSQL("INSERT INTO Secciones(codigo,asignatura_id,docente_id,periodo_id,cupo,estado) VALUES(?,?,?,?,?,'ACTIVA')",
                codigo,asignatura,docente,periodo,cupo);
            List<Map<String,Object>> ids=dao.consultar("SELECT id FROM Secciones WHERE codigo=? ORDER BY id DESC LIMIT 1",codigo);
            if(ids.isEmpty()) throw new IllegalStateException("No se pudo recuperar la sección creada.");
            long id=Long.parseLong(ids.get(0).get("id").toString());
            for(String b:bloques) {
                String[] x=b.split("\\|",-1);
                String tipo=x[0];
                String dias=x[1];
                String fecha=x[2].isBlank()?null:x[2];
                String hi=x[3], hf=x[4], aula=x[5];
                if("RECURRENTE".equals(tipo)) {
                    for(String dia:dias.split(",")) {
                        dao.ejecutarSQL("INSERT INTO HorariosSeccion(seccion_id,tipo,dia_semana,fecha,hora_inicio,hora_fin,aula) VALUES(?,?,?,?,?,?,?)",
                            id,tipo,dia,null,hi,hf,aula);
                    }
                } else {
                    dao.ejecutarSQL("INSERT INTO HorariosSeccion(seccion_id,tipo,dia_semana,fecha,hora_inicio,hora_fin,aula) VALUES(?,?,?,?,?,?,?)",
                        id,tipo,null,fecha,hi,hf,aula);
                }
            }
            ra.addFlashAttribute("ok","Sección creada con "+bloques.size()+" bloque(s) de horario.");
        } catch(Exception e) {
            ra.addFlashAttribute("error","No se pudo guardar la sección: "+e.getMessage());
        }
        return "redirect:/admin/secciones";
    }

    private void validarBloque(String b) {
        String[] x=b.split("\\|",-1);
        if(x.length!=6) throw new IllegalArgumentException("Formato de horario inválido.");
        if(!List.of("RECURRENTE","FECHA").contains(x[0])) throw new IllegalArgumentException("Tipo de horario inválido.");
        if("RECURRENTE".equals(x[0]) && x[1].isBlank()) throw new IllegalArgumentException("Seleccione al menos un día.");
        if("FECHA".equals(x[0]) && x[2].isBlank()) throw new IllegalArgumentException("Seleccione la fecha específica.");
        LocalTime ini=LocalTime.parse(x[3]), fin=LocalTime.parse(x[4]);
        if(!fin.isAfter(ini)) throw new IllegalArgumentException("La hora final debe ser posterior a la inicial.");
    }
    private String req(Map<String,String> p,String k) {
        String v=p.get(k); if(v==null || v.isBlank()) throw new IllegalArgumentException("Complete el campo "+k+"."); return v.trim();
    }
}
