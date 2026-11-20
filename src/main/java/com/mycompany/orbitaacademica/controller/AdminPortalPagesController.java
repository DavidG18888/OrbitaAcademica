package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/admin")
public class AdminPortalPagesController {
    private final TursoDAO dao;

    public AdminPortalPagesController(TursoDAO dao) {
        this.dao = dao;
    }

    @GetMapping("/finanzas")
    public String finanzas(Model m) {
        m.addAttribute("secciones", dao.consultar("SELECT s.id,s.codigo,a.nombre asignatura,p.nombre periodo FROM Secciones s JOIN Asignaturas a ON a.id=s.asignatura_id JOIN Periodos p ON p.id=s.periodo_id WHERE COALESCE(s.estado,'ACTIVA')='ACTIVA' ORDER BY a.nombre,s.codigo"));
        m.addAttribute("cuotas", dao.consultar("SELECT c.*,COALESCE(s.codigo,'General') seccion,COALESCE(a.nombre,'Cuota institucional') asignatura FROM Cuotas c LEFT JOIN Secciones s ON s.id=c.seccion_id LEFT JOIN Asignaturas a ON a.id=s.asignatura_id ORDER BY c.id DESC"));
        m.addAttribute("pagos", dao.consultar("SELECT x.*,e.codigo,e.nombre||' '||e.apellido estudiante,c.concepto,c.periodo_mes,c.monto,COALESCE(s.codigo,'General') seccion,COALESCE(a.nombre,'Cuota') asignatura FROM PagosCuota x JOIN Estudiantes e ON e.id=x.estudiante_id JOIN Cuotas c ON c.id=x.cuota_id LEFT JOIN Secciones s ON s.id=c.seccion_id LEFT JOIN Asignaturas a ON a.id=s.asignatura_id WHERE x.estado='PENDIENTE' ORDER BY x.fecha_pago"));
        m.addAttribute("pagosHistorial", dao.consultar("SELECT x.*,e.codigo,e.nombre||' '||e.apellido estudiante,c.concepto,c.periodo_mes,c.monto,COALESCE(s.codigo,'General') seccion,COALESCE(a.nombre,'Cuota') asignatura FROM PagosCuota x JOIN Estudiantes e ON e.id=x.estudiante_id JOIN Cuotas c ON c.id=x.cuota_id LEFT JOIN Secciones s ON s.id=c.seccion_id LEFT JOIN Asignaturas a ON a.id=s.asignatura_id WHERE x.estado<>'PENDIENTE' ORDER BY x.fecha_revision DESC LIMIT 50"));
        return "admin/finanzas";
    }

    @GetMapping("/expedientes")
    public String expedientes(@RequestParam(defaultValue = "pendientes") String tab, Model m) {
        boolean verAprobados = "aprobados".equalsIgnoreCase(tab);
        m.addAttribute("tab", verAprobados ? "aprobados" : "pendientes");
        m.addAttribute("contPendientes", dao.consultar("SELECT COUNT(*) n FROM DocumentosEstudiante WHERE estado IN ('PENDIENTE','EN_REVISION')").get(0).get("n"));
        m.addAttribute("contAprobados", dao.consultar("SELECT COUNT(*) n FROM DocumentosEstudiante WHERE estado = 'APROBADO'").get(0).get("n"));

        if (verAprobados) {
            m.addAttribute("documentos", dao.consultar("SELECT x.*,e.codigo,e.nombre||' '||e.apellido estudiante FROM DocumentosEstudiante x JOIN Estudiantes e ON e.id=x.estudiante_id WHERE x.estado = 'APROBADO' ORDER BY x.fecha_revision DESC, x.id DESC LIMIT 100"));
        } else {
            m.addAttribute("documentos", dao.consultar("SELECT x.*,e.codigo,e.nombre||' '||e.apellido estudiante FROM DocumentosEstudiante x JOIN Estudiantes e ON e.id=x.estudiante_id WHERE x.estado IN ('PENDIENTE','EN_REVISION') ORDER BY x.fecha_carga ASC"));
        }
        return "admin/expedientes";
    }

    @GetMapping("/soporte")
    public String soporte(@RequestParam(defaultValue = "activos") String filtroTickets, Model m) {
        boolean verCerrados = "cerrados".equalsIgnoreCase(filtroTickets);
        m.addAttribute("filtroTickets", verCerrados ? "cerrados" : "activos");
        String whereTicket = verCerrados
                ? "WHERE t.estado IN ('RESUELTO','RECHAZADO')"
                : "WHERE t.estado IN ('PENDIENTE','EN_PROCESO')";
        List<Map<String, Object>> ts = dao.consultar("SELECT t.*,e.codigo,e.nombre||' '||e.apellido estudiante FROM Tickets t JOIN Estudiantes e ON e.id=t.estudiante_id " + whereTicket + " ORDER BY t.creado_en DESC LIMIT 80");
        for (Map<String, Object> t : ts) {
            t.put("respuestas", dao.consultar("SELECT r.*,u.usuario,u.rol FROM TicketRespuestas r JOIN Usuarios u ON u.id=r.usuario_id WHERE r.ticket_id=? ORDER BY r.id", t.get("id")));
        }
        m.addAttribute("tickets", ts);
        m.addAttribute("ausencias", dao.consultar("SELECT r.*,e.codigo,e.nombre||' '||e.apellido estudiante,s.codigo seccion,a.nombre asignatura FROM RevisionesAusencia r JOIN Matriculas m ON m.id=r.matricula_id JOIN Estudiantes e ON e.id=m.estudiante_id JOIN Secciones s ON s.id=m.seccion_id JOIN Asignaturas a ON a.id=s.asignatura_id WHERE r.estado_revision NOT IN ('CONFIRMADA','DESCARTADA') ORDER BY r.id DESC"));
        m.addAttribute("eventos", dao.consultar("SELECT * FROM EventosAcademicos ORDER BY fecha_inicio DESC LIMIT 30"));
        m.addAttribute("waitlist", dao.consultar("SELECT l.*,e.codigo,e.nombre||' '||e.apellido estudiante,s.codigo seccion,a.nombre asignatura FROM ListaEspera l JOIN Estudiantes e ON e.id=l.estudiante_id JOIN Secciones s ON s.id=l.seccion_id JOIN Asignaturas a ON a.id=s.asignatura_id WHERE l.estado IN ('ESPERA','NOTIFICADO') ORDER BY s.id,l.posicion"));
        m.addAttribute("bitacora", dao.consultar("SELECT b.*,u.usuario FROM BitacoraSistema b LEFT JOIN Usuarios u ON u.id=b.usuario_id ORDER BY b.id DESC LIMIT 60"));
        m.addAttribute("evaluaciones", dao.consultar("SELECT d.nombre||' '||d.apellido docente,a.nombre asignatura,sc.codigo seccion,ROUND(AVG(ev.puntuacion),2) promedio,COUNT(*) respuestas FROM EvaluacionesDocente ev JOIN Docentes d ON d.id=ev.docente_id JOIN Secciones sc ON sc.id=ev.seccion_id JOIN Asignaturas a ON a.id=sc.asignatura_id GROUP BY d.id,sc.id ORDER BY promedio DESC"));
        return "admin/soporte";
    }
}
