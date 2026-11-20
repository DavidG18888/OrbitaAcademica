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
        m.addAttribute("periodosActivos", dao.consultar("SELECT id, nombre, COALESCE(costo_matricula, 0) AS costo_matricula FROM Periodos WHERE UPPER(COALESCE(estado,'ACTIVO'))='ACTIVO' ORDER BY id DESC"));
        m.addAttribute("periodosTodos", dao.consultar("SELECT id, nombre, COALESCE(costo_matricula, 0) AS costo_matricula, estado FROM Periodos ORDER BY id DESC"));
        m.addAttribute("cuotas", dao.consultar("SELECT c.*, p.nombre AS periodo FROM Cuotas c JOIN Periodos p ON p.id=c.periodo_id ORDER BY p.id DESC, c.parcial ASC"));
        m.addAttribute("pagosMatricula", dao.consultar("SELECT pm.*, e.codigo, e.nombre||' '||e.apellido AS estudiante, p.nombre AS periodo FROM PagosMatriculaPeriodo pm JOIN Estudiantes e ON e.id=pm.estudiante_id JOIN Periodos p ON p.id=pm.periodo_id WHERE pm.estado='PENDIENTE' ORDER BY pm.fecha_pago ASC"));
        m.addAttribute("pagos", dao.consultar("SELECT x.*, e.codigo, e.nombre||' '||e.apellido AS estudiante, c.concepto, c.parcial, c.monto, p.nombre AS periodo FROM PagosCuota x JOIN Estudiantes e ON e.id=x.estudiante_id JOIN Cuotas c ON c.id=x.cuota_id JOIN Periodos p ON p.id=c.periodo_id WHERE x.estado='PENDIENTE' ORDER BY x.fecha_pago ASC"));
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

    @GetMapping("/calendario")
    public String calendario(Model m) {
        m.addAttribute("eventos", dao.consultar("SELECT * FROM EventosAcademicos ORDER BY fecha_inicio DESC LIMIT 50"));
        return "admin/calendario";
    }

    @GetMapping("/evaluaciones")
    public String evaluaciones(Model m) {
        m.addAttribute("evaluaciones", dao.consultar(
                "SELECT d.nombre||' '||d.apellido AS docente, a.nombre AS asignatura, sc.codigo AS seccion, "
                + "ROUND(AVG(ev.puntuacion),2) AS promedio, COUNT(*) AS respuestas "
                + "FROM EvaluacionesDocente ev JOIN Docentes d ON d.id=ev.docente_id JOIN Secciones sc ON sc.id=ev.seccion_id JOIN Asignaturas a ON a.id=sc.asignatura_id "
                + "GROUP BY d.id, sc.id ORDER BY promedio DESC"));
        m.addAttribute("comentarios", dao.consultar(
                "SELECT d.nombre||' '||d.apellido AS docente, a.nombre AS asignatura, sc.codigo AS seccion, ev.puntuacion, ev.comentario, ev.creada_en "
                + "FROM EvaluacionesDocente ev JOIN Docentes d ON d.id=ev.docente_id JOIN Secciones sc ON sc.id=ev.seccion_id JOIN Asignaturas a ON a.id=sc.asignatura_id "
                + "WHERE ev.comentario IS NOT NULL AND TRIM(ev.comentario) <> '' ORDER BY ev.id DESC LIMIT 50"));
        return "admin/evaluaciones";
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
        m.addAttribute("waitlist", dao.consultar("SELECT l.*,e.codigo,e.nombre||' '||e.apellido estudiante,s.codigo seccion,a.nombre asignatura FROM ListaEspera l JOIN Estudiantes e ON e.id=l.estudiante_id JOIN Secciones s ON s.id=l.seccion_id JOIN Asignaturas a ON a.id=s.asignatura_id WHERE l.estado IN ('ESPERA','NOTIFICADO') ORDER BY s.id,l.posicion"));
        m.addAttribute("bitacora", dao.consultar("SELECT b.*,u.usuario FROM BitacoraSistema b LEFT JOIN Usuarios u ON u.id=b.usuario_id ORDER BY b.id DESC LIMIT 60"));
        return "admin/soporte";
    }
}
