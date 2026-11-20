package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import com.mycompany.orbitaacademica.service.PrivateFileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/admin/servicios")
public class AdminServicesController {
    private final TursoDAO dao;
    private final PrivateFileService files;

    public AdminServicesController(TursoDAO dao, PrivateFileService files) {
        this.dao = dao;
        this.files = files;
    }

    private long user(HttpSession s) {
        return Long.parseLong(String.valueOf(dao.consultar("SELECT id FROM Usuarios WHERE usuario=?", s.getAttribute("usuario")).get(0).get("id")));
    }

    @GetMapping
    public String home() {
        return "redirect:/admin/finanzas";
    }

    @PostMapping("/cuota")
    public String cuota(@RequestParam long seccionId, @RequestParam String concepto, @RequestParam String periodoMes,
                        @RequestParam double monto, @RequestParam(required = false) String fechaLimite,
                        @RequestParam(required = false) String instrucciones, HttpSession s, RedirectAttributes ra) {
        try {
            if (monto <= 0) throw new IllegalArgumentException("El monto debe ser mayor a cero.");
            dao.ejecutarSQL("INSERT INTO Cuotas(seccion_id,concepto,periodo_mes,monto,fecha_limite,instrucciones,activa) VALUES(?,?,?,?,?,?,1)",
                    seccionId, concepto.trim(), periodoMes.trim(), monto, blank(fechaLimite), blank(instrucciones));
            List<Map<String, Object>> users = dao.consultar(
                    "SELECT DISTINCT u.id FROM Usuarios u JOIN Estudiantes e ON e.usuario_id=u.id JOIN Matriculas m ON m.estudiante_id=e.id "
                    + "WHERE m.seccion_id=? AND m.estado='ACTIVA' AND UPPER(COALESCE(e.estado,'ACTIVO'))='ACTIVO'", seccionId);
            for (Map<String, Object> u : users) {
                dao.ejecutarSQL("INSERT INTO Notificaciones(usuario_id,titulo,mensaje,enlace) VALUES(?,?,?,?)",
                        u.get("id"), "Nueva cuota habilitada", concepto + " · " + periodoMes + " · L " + String.format(Locale.US, "%.2f", monto), "/estudiante/pagos");
            }
            ra.addFlashAttribute("ok", "Cuota activada y estudiantes matriculados notificados.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudo activar la cuota: " + e.getMessage());
        }
        return "redirect:/admin/finanzas";
    }

    @PostMapping("/cuota/{id}/estado")
    public String estadoCuota(@PathVariable long id, @RequestParam int activa, RedirectAttributes ra) {
        dao.ejecutarSQL("UPDATE Cuotas SET activa=? WHERE id=?", activa, id);
        ra.addFlashAttribute("ok", "Estado de cuota actualizado.");
        return "redirect:/admin/finanzas";
    }

    @PostMapping("/pago/{id}/revisar")
    public String pago(@PathVariable long id, @RequestParam String estado, @RequestParam(required = false) String observacion, HttpSession s, RedirectAttributes ra) {
        try {
            if (!List.of("APROBADO", "RECHAZADO").contains(estado)) throw new IllegalArgumentException("Estado inválido.");
            dao.ejecutarSQL("UPDATE PagosCuota SET estado=?,observacion=?,revisado_por=?,fecha_revision=CURRENT_TIMESTAMP WHERE id=?", estado, blank(observacion), user(s), id);
            var rows = dao.consultar("SELECT e.usuario_id FROM PagosCuota p JOIN Estudiantes e ON e.id=p.estudiante_id WHERE p.id=?", id);
            if (!rows.isEmpty()) {
                dao.ejecutarSQL("INSERT INTO Notificaciones(usuario_id,titulo,mensaje,enlace) VALUES(?,?,?,?)",
                        rows.get(0).get("usuario_id"), "Resultado de comprobante de pago",
                        "Su comprobante fue " + estado.toLowerCase() + ". " + (observacion == null ? "" : observacion), "/estudiante/pagos");
            }
            ra.addFlashAttribute("ok", "Comprobante revisado correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/finanzas";
    }

    @PostMapping("/documento/{id}/revisar")
    public String documento(@PathVariable long id, @RequestParam String estado, @RequestParam(required = false) String observacion,
                            @RequestParam(defaultValue = "pendientes") String tab, HttpSession s, RedirectAttributes ra) {
        try {
            if (!List.of("APROBADO", "RECHAZADO", "EN_REVISION", "PENDIENTE").contains(estado)) {
                throw new IllegalArgumentException("Estado inválido.");
            }
            dao.ejecutarSQL("UPDATE DocumentosEstudiante SET estado=?,observacion=?,revisado_por=?,fecha_revision=CURRENT_TIMESTAMP WHERE id=?",
                    estado, blank(observacion), user(s), id);
            var rows = dao.consultar("SELECT e.usuario_id, d.tipo FROM DocumentosEstudiante d JOIN Estudiantes e ON e.id=d.estudiante_id WHERE d.id=?", id);
            if (!rows.isEmpty()) {
                dao.ejecutarSQL("INSERT INTO Notificaciones(usuario_id,titulo,mensaje,enlace) VALUES(?,?,?,?)",
                        rows.get(0).get("usuario_id"), "Revisión de expediente digital",
                        "Documento " + rows.get(0).get("tipo") + ": " + estado + ". " + (observacion == null ? "" : observacion), "/estudiante/documentos");
            }
            ra.addFlashAttribute("ok", "Estado del documento actualizado por administración.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/expedientes?tab=" + tab;
    }

    @PostMapping("/ticket/{id}/respuesta")
    public String ticket(@PathVariable long id, @RequestParam String mensaje, @RequestParam String estado, HttpSession s, RedirectAttributes ra) {
        try {
            if (!List.of("PENDIENTE", "EN_PROCESO", "RESUELTO", "RECHAZADO").contains(estado)) {
                throw new IllegalArgumentException("Estado de ticket inválido.");
            }
            dao.ejecutarSQL("INSERT INTO TicketRespuestas(ticket_id,usuario_id,mensaje) VALUES(?,?,?)", id, user(s), mensaje.trim());
            dao.ejecutarSQL("UPDATE Tickets SET estado=?,actualizado_en=CURRENT_TIMESTAMP WHERE id=?", estado, id);
            var rows = dao.consultar("SELECT e.usuario_id, t.asunto FROM Tickets t JOIN Estudiantes e ON e.id=t.estudiante_id WHERE t.id=?", id);
            if (!rows.isEmpty()) {
                dao.ejecutarSQL("INSERT INTO Notificaciones(usuario_id,titulo,mensaje,enlace) VALUES(?,?,?,?)",
                        rows.get(0).get("usuario_id"), "Actualización de ticket (" + estado + ")",
                        rows.get(0).get("asunto") + ": " + mensaje.trim(), "/estudiante/soporte");
            }
            ra.addFlashAttribute("ok", "Respuesta enviada y estatus actualizado a " + estado + ".");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/soporte";
    }

    @PostMapping("/ausencia/{id}/contacto")
    public String contacto(@PathVariable long id, @RequestParam String medio, @RequestParam String resultado, @RequestParam(required = false) String observacion, HttpSession s, RedirectAttributes ra) {
        try {
            dao.ejecutarSQL("INSERT INTO ContactosAusencia(revision_id,usuario_id,medio,resultado,observacion) VALUES(?,?,?,?,?)", id, user(s), medio, resultado, blank(observacion));
            dao.ejecutarSQL("UPDATE RevisionesAusencia SET intentos_contacto=intentos_contacto+1,estado_revision='EN_CONTACTO',resultado_contacto=?,fecha_revision=CURRENT_TIMESTAMP,revisado_por=? WHERE id=?", resultado, user(s), id);
            ra.addFlashAttribute("ok", "Contacto registrado.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/soporte";
    }

    @PostMapping("/ausencia/{id}/resolver")
    public String resolver(@PathVariable long id, @RequestParam String decision, HttpSession s, RedirectAttributes ra) {
        try {
            if (!List.of("CONFIRMADA", "DESCARTADA").contains(decision)) throw new IllegalArgumentException("Decisión inválida.");
            dao.ejecutarSQL("UPDATE RevisionesAusencia SET estado_revision=?,fecha_revision=CURRENT_TIMESTAMP,revisado_por=? WHERE id=?", decision, user(s), id);
            if ("CONFIRMADA".equals(decision)) {
                dao.ejecutarSQL("UPDATE Matriculas SET estado='NO_SE_PRESENTO',fecha_retiro=date('now'),motivo_retiro=COALESCE((SELECT motivo FROM RevisionesAusencia WHERE id=?),'Reportado como No se presentó') WHERE id=(SELECT matricula_id FROM RevisionesAusencia WHERE id=?)", id, id);
            }
            ra.addFlashAttribute("ok", "Caso de ausencia resuelto.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/soporte";
    }

    @PostMapping("/evento")
    public String evento(@RequestParam String titulo, @RequestParam(required = false) String descripcion,
                         @RequestParam String inicio, @RequestParam(required = false) String fin,
                         @RequestParam(required = false) String lugar,
                         @RequestParam(required = false) MultipartFile imagen,
                         HttpSession s, RedirectAttributes ra) {
        try {
            String imgUrl = null;
            if (imagen != null && !imagen.isEmpty()) {
                imgUrl = files.save(imagen, "eventos", "jpg", "jpeg", "png", "webp");
            }
            dao.ejecutarSQL("INSERT INTO EventosAcademicos(titulo,descripcion,fecha_inicio,fecha_fin,lugar,imagen_url,creado_por) VALUES(?,?,?,?,?,?,?)",
                    titulo.trim(), blank(descripcion), inicio, blank(fin), blank(lugar), imgUrl, user(s));
            ra.addFlashAttribute("ok", "Evento publicado en el calendario institucional.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/soporte";
    }

    @PostMapping("/evento/{id}/cancelar")
    public String cancelarEvento(@PathVariable long id, RedirectAttributes ra) {
        dao.ejecutarSQL("UPDATE EventosAcademicos SET estado='CANCELADO' WHERE id=?", id);
        ra.addFlashAttribute("ok", "Evento cancelado.");
        return "redirect:/admin/soporte";
    }

    @PostMapping("/espera/{id}/estado")
    public String espera(@PathVariable long id, @RequestParam String estado, RedirectAttributes ra) {
        if (List.of("ESPERA", "NOTIFICADO", "MATRICULADO", "CANCELADO").contains(estado)) {
            dao.ejecutarSQL("UPDATE ListaEspera SET estado=? WHERE id=?", estado, id);
        }
        ra.addFlashAttribute("ok", "Lista de espera actualizada.");
        return "redirect:/admin/soporte";
    }

    private Object blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
