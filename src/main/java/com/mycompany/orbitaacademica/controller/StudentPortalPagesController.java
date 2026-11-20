package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

@Controller
@RequestMapping("/estudiante")
public class StudentPortalPagesController {
    private final TursoDAO dao;

    public StudentPortalPagesController(TursoDAO dao) {
        this.dao = dao;
    }

    private long uid(HttpSession s) {
        return Long.parseLong(String.valueOf(dao.consultar("SELECT id FROM Usuarios WHERE usuario=?", s.getAttribute("usuario")).get(0).get("id")));
    }

    private long sid(HttpSession s) {
        return Long.parseLong(String.valueOf(dao.consultar("SELECT id FROM Estudiantes WHERE usuario_id=?", uid(s)).get(0).get("id")));
    }

    @GetMapping("/pagos")
    public String pagos(Model m, HttpSession s) {
        long st = sid(s);

        // Estado de matrícula del período activo
        List<Map<String, Object>> per = dao.consultar("SELECT id, nombre, COALESCE(costo_matricula, 0) AS costo_matricula FROM Periodos WHERE UPPER(COALESCE(estado,'ACTIVO'))='ACTIVO' ORDER BY id DESC LIMIT 1");
        if (!per.isEmpty()) {
            long pid = Long.parseLong(String.valueOf(per.get(0).get("id")));
            long previas = Long.parseLong(String.valueOf(dao.consultar("SELECT COUNT(*) AS n FROM Matriculas m JOIN Secciones sc ON sc.id=m.seccion_id WHERE m.estudiante_id=? AND sc.periodo_id<>?", st, pid).get(0).get("n")));
            boolean esNuevo = previas == 0;
            m.addAttribute("periodoActivo", per.get(0));
            m.addAttribute("esNuevo", esNuevo);
            List<Map<String, Object>> pagoMat = dao.consultar("SELECT * FROM PagosMatriculaPeriodo WHERE periodo_id=? AND estudiante_id=?", pid, st);
            m.addAttribute("pagoMatricula", pagoMat.isEmpty() ? null : pagoMat.get(0));
        }

        // Cuotas generales por parcial del período activo
        m.addAttribute("cuotas", dao.consultar(
                "SELECT c.*, p.nombre AS periodo, "
                + "(SELECT pg.estado FROM PagosCuota pg WHERE pg.cuota_id=c.id AND pg.estudiante_id=? ORDER BY pg.id DESC LIMIT 1) AS pago_estado "
                + "FROM Cuotas c JOIN Periodos p ON p.id=c.periodo_id "
                + "WHERE c.activa=1 AND UPPER(COALESCE(p.estado,'ACTIVO'))='ACTIVO' "
                + "ORDER BY c.parcial ASC", st));

        m.addAttribute("pagos", dao.consultar(
                "SELECT pg.*, c.concepto, c.parcial, c.monto, p.nombre AS periodo "
                + "FROM PagosCuota pg JOIN Cuotas c ON c.id=pg.cuota_id JOIN Periodos p ON p.id=c.periodo_id "
                + "WHERE pg.estudiante_id=? ORDER BY pg.id DESC", st));
        return "estudiante/pagos";
    }

    @GetMapping("/documentos")
    public String documentos(Model m, HttpSession s) {
        List<Map<String, Object>> docs = dao.consultar("SELECT * FROM DocumentosEstudiante WHERE estudiante_id=? ORDER BY id DESC", sid(s));
        Map<String, Map<String, Object>> porTipo = new HashMap<>();
        for (Map<String, Object> d : docs) {
            porTipo.putIfAbsent(String.valueOf(d.get("tipo")), d);
        }
        m.addAttribute("documentos", docs);
        m.addAttribute("porTipo", porTipo);
        m.addAttribute("foto", dao.consultar("SELECT foto_url FROM PerfilArchivos WHERE usuario_id=?", uid(s)));
        return "estudiante/documentos";
    }

    @GetMapping("/soporte")
    public String soporte(@RequestParam(required = false) String categoria,
                          @RequestParam(required = false) String asunto,
                          Model m, HttpSession s) {
        long st = sid(s);
        m.addAttribute("tickets", tickets(st));
        m.addAttribute("preCategoria", categoria != null ? categoria : "");
        m.addAttribute("preAsunto", asunto != null ? asunto : "");
        m.addAttribute("evaluables", dao.consultar("SELECT m.id matricula_id,s.id seccion_id,d.id docente_id,d.nombre||' '||d.apellido docente,a.nombre asignatura FROM Matriculas m JOIN Secciones s ON s.id=m.seccion_id JOIN Docentes d ON d.id=s.docente_id JOIN Asignaturas a ON a.id=s.asignatura_id WHERE m.estudiante_id=? AND m.estado='ACTIVA' AND NOT EXISTS(SELECT 1 FROM EvaluacionesDocente ev WHERE ev.estudiante_id=m.estudiante_id AND ev.seccion_id=s.id)", st));
        m.addAttribute("eventos", dao.consultar("SELECT * FROM EventosAcademicos WHERE COALESCE(estado,'ACTIVO')='ACTIVO' ORDER BY fecha_inicio ASC LIMIT 30"));
        m.addAttribute("notificaciones", dao.consultar("SELECT * FROM Notificaciones WHERE usuario_id=? ORDER BY id DESC LIMIT 20", uid(s)));
        return "estudiante/soporte";
    }

    private List<Map<String, Object>> tickets(long st) {
        List<Map<String, Object>> ts = dao.consultar("SELECT * FROM Tickets WHERE estudiante_id=? ORDER BY id DESC", st);
        for (Map<String, Object> t : ts) {
            t.put("respuestas", dao.consultar("SELECT r.*,u.usuario,u.rol FROM TicketRespuestas r JOIN Usuarios u ON u.id=r.usuario_id WHERE r.ticket_id=? ORDER BY r.id", t.get("id")));
        }
        return ts;
    }
}
