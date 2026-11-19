package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.*;

@Controller
@RequestMapping("/estudiante")
public class StudentPortalPagesController {
    private final TursoDAO dao;
    public StudentPortalPagesController(TursoDAO dao){this.dao=dao;}
    private long uid(HttpSession s){return Long.parseLong(String.valueOf(dao.consultar("SELECT id FROM Usuarios WHERE usuario=?",s.getAttribute("usuario")).get(0).get("id")));}
    private long sid(HttpSession s){return Long.parseLong(String.valueOf(dao.consultar("SELECT id FROM Estudiantes WHERE usuario_id=?",uid(s)).get(0).get("id")));}
    @GetMapping("/pagos") public String pagos(Model m,HttpSession s){long st=sid(s);m.addAttribute("cuotas",dao.consultar("SELECT c.*,sc.codigo seccion,a.nombre asignatura,(SELECT p.estado FROM PagosCuota p WHERE p.cuota_id=c.id AND p.estudiante_id=? ORDER BY p.id DESC LIMIT 1) pago_estado FROM Cuotas c JOIN Secciones sc ON sc.id=c.seccion_id JOIN Asignaturas a ON a.id=sc.asignatura_id JOIN Matriculas ma ON ma.seccion_id=sc.id AND ma.estudiante_id=? AND ma.estado='ACTIVA' WHERE c.activa=1 ORDER BY c.periodo_mes DESC",st,st));m.addAttribute("pagos",dao.consultar("SELECT p.*,c.concepto,c.periodo_mes,c.monto,sc.codigo seccion,a.nombre asignatura FROM PagosCuota p JOIN Cuotas c ON c.id=p.cuota_id JOIN Secciones sc ON sc.id=c.seccion_id JOIN Asignaturas a ON a.id=sc.asignatura_id WHERE p.estudiante_id=? ORDER BY p.id DESC",st));return "estudiante/pagos";}
    @GetMapping("/documentos") public String documentos(Model m,HttpSession s){m.addAttribute("documentos",dao.consultar("SELECT * FROM DocumentosEstudiante WHERE estudiante_id=? ORDER BY id DESC",sid(s)));m.addAttribute("foto",dao.consultar("SELECT foto_url FROM PerfilArchivos WHERE usuario_id=?",uid(s)));return "estudiante/documentos";}
    @GetMapping("/soporte") public String soporte(Model m,HttpSession s){long st=sid(s);m.addAttribute("tickets",tickets(st));m.addAttribute("evaluables",dao.consultar("SELECT m.id matricula_id,s.id seccion_id,d.id docente_id,d.nombre||' '||d.apellido docente,a.nombre asignatura FROM Matriculas m JOIN Secciones s ON s.id=m.seccion_id JOIN Docentes d ON d.id=s.docente_id JOIN Asignaturas a ON a.id=s.asignatura_id WHERE m.estudiante_id=? AND m.estado='ACTIVA' AND NOT EXISTS(SELECT 1 FROM EvaluacionesDocente ev WHERE ev.estudiante_id=m.estudiante_id AND ev.seccion_id=s.id)",st));m.addAttribute("eventos",dao.consultar("SELECT * FROM EventosAcademicos WHERE estado='ACTIVO' ORDER BY fecha_inicio LIMIT 30"));m.addAttribute("notificaciones",dao.consultar("SELECT * FROM Notificaciones WHERE usuario_id=? ORDER BY id DESC LIMIT 20",uid(s)));return "estudiante/soporte";}
    private List<Map<String,Object>> tickets(long st){List<Map<String,Object>> ts=dao.consultar("SELECT * FROM Tickets WHERE estudiante_id=? ORDER BY id DESC",st);for(Map<String,Object> t:ts)t.put("respuestas",dao.consultar("SELECT r.*,u.usuario FROM TicketRespuestas r JOIN Usuarios u ON u.id=r.usuario_id WHERE r.ticket_id=? ORDER BY r.id",t.get("id")));return ts;}
}
