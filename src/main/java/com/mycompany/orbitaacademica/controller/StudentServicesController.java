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
@RequestMapping("/estudiante/servicios")
public class StudentServicesController {
    private final TursoDAO dao;
    private final PrivateFileService files;

    public StudentServicesController(TursoDAO dao, PrivateFileService files) {
        this.dao = dao;
        this.files = files;
    }

    private long user(HttpSession s) {
        return Long.parseLong(String.valueOf(dao.consultar("SELECT id FROM Usuarios WHERE usuario=?", s.getAttribute("usuario")).get(0).get("id")));
    }

    private long student(HttpSession s) {
        return Long.parseLong(String.valueOf(dao.consultar("SELECT id FROM Estudiantes WHERE usuario_id=?", user(s)).get(0).get("id")));
    }

    @GetMapping
    public String home() {
        return "redirect:/estudiante/pagos";
    }

    @PostMapping("/documento")
    public String documento(@RequestParam String tipo, @RequestParam MultipartFile archivo, HttpSession s, RedirectAttributes ra) {
        try {
            if (!List.of("TITULO_GRADUACION", "PARTIDA_NACIMIENTO", "DNI").contains(tipo)) {
                throw new IllegalArgumentException("Tipo de documento inválido.");
            }
            long sid = student(s);
            List<Map<String, Object>> yaExiste = dao.consultar(
                    "SELECT id, estado FROM DocumentosEstudiante WHERE estudiante_id=? AND tipo=? ORDER BY id DESC LIMIT 1", sid, tipo);
            if (!yaExiste.isEmpty()) {
                throw new IllegalArgumentException("Este documento ya fue cargado en tu expediente. Solo la administración puede modificarlo o cambiar su estado.");
            }
            String ref = files.save(archivo, "documentos", "pdf");
            dao.ejecutarSQL("INSERT INTO DocumentosEstudiante(estudiante_id,tipo,archivo_url,nombre_archivo) VALUES(?,?,?,?)", sid, tipo, ref, archivo.getOriginalFilename());
            notifyAdmins("Nuevo documento pendiente", "Un estudiante cargó " + tipo + " para revisión.", "/admin/expedientes");
            ra.addFlashAttribute("ok", "Documento enviado correctamente. Queda bloqueado para edición del estudiante.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/estudiante/documentos";
    }

    @PostMapping("/pago")
    public String pago(@RequestParam long cuotaId, @RequestParam MultipartFile archivo, HttpSession s, RedirectAttributes ra) {
        try {
            String ref = files.save(archivo, "pagos", "pdf");
            long sid = student(s);
            long valido = Long.parseLong(String.valueOf(dao.consultar("SELECT COUNT(*) n FROM Cuotas c JOIN Matriculas m ON m.seccion_id=c.seccion_id WHERE c.id=? AND c.activa=1 AND m.estudiante_id=? AND m.estado='ACTIVA'", cuotaId, sid).get(0).get("n")));
            if (valido == 0) throw new IllegalArgumentException("La cuota no corresponde a una clase que tengas matriculada.");
            dao.ejecutarSQL("INSERT INTO PagosCuota(cuota_id,estudiante_id,comprobante_url,nombre_archivo) VALUES(?,?,?,?)", cuotaId, sid, ref, archivo.getOriginalFilename());
            notifyAdmins("Comprobante de pago pendiente", "Se recibió un comprobante de cuota.", "/admin/finanzas");
            ra.addFlashAttribute("ok", "Comprobante enviado correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/estudiante/pagos";
    }

    @PostMapping("/oferta/{id}/espera")
    public String espera(@PathVariable long id, HttpSession s, RedirectAttributes ra) {
        try {
            long sid = student(s);
            long exists = Long.parseLong(String.valueOf(dao.consultar("SELECT COUNT(*) n FROM Secciones WHERE id=? AND estado='ACTIVA'", id).get(0).get("n")));
            if (exists == 0) throw new IllegalArgumentException("Sección no disponible.");
            long pos = Long.parseLong(String.valueOf(dao.consultar("SELECT COALESCE(MAX(posicion),0)+1 n FROM ListaEspera WHERE seccion_id=?", id).get(0).get("n")));
            dao.ejecutarSQL("INSERT INTO ListaEspera(estudiante_id,seccion_id,posicion) VALUES(?,?,?)", sid, id, pos);
            ra.addFlashAttribute("ok", "Se agregó a la lista de espera.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/estudiante/oferta";
    }

    @PostMapping("/ticket")
    public String ticket(@RequestParam String asunto, @RequestParam String categoria, @RequestParam String descripcion, HttpSession s, RedirectAttributes ra) {
        try {
            dao.ejecutarSQL("INSERT INTO Tickets(estudiante_id,asunto,categoria,descripcion,estado) VALUES(?,?,?,?,'PENDIENTE')", student(s), asunto.trim(), categoria.trim(), descripcion.trim());
            notifyAdmins("Nuevo ticket de soporte", asunto.trim(), "/admin/soporte");
            ra.addFlashAttribute("ok", "Ticket creado en estado Pendiente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/estudiante/soporte";
    }

    @PostMapping("/ticket/{id}/respuesta")
    public String responder(@PathVariable long id, @RequestParam String mensaje, HttpSession s, RedirectAttributes ra) {
        try {
            long sid = student(s);
            List<Map<String, Object>> rows = dao.consultar("SELECT id, estado FROM Tickets WHERE id=? AND estudiante_id=?", id, sid);
            if (rows.isEmpty()) throw new IllegalArgumentException("Ticket no encontrado.");
            String estadoActual = String.valueOf(rows.get(0).get("estado"));
            if ("RESUELTO".equalsIgnoreCase(estadoActual) || "RECHAZADO".equalsIgnoreCase(estadoActual)) {
                throw new IllegalArgumentException("Este ticket ya se encuentra " + estadoActual + " y no admite más respuestas.");
            }
            dao.ejecutarSQL("INSERT INTO TicketRespuestas(ticket_id,usuario_id,mensaje) VALUES(?,?,?)", id, user(s), mensaje.trim());
            dao.ejecutarSQL("UPDATE Tickets SET estado='PENDIENTE',actualizado_en=CURRENT_TIMESTAMP WHERE id=?", id);
            ra.addFlashAttribute("ok", "Respuesta enviada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/estudiante/soporte";
    }

    @PostMapping("/evaluacion")
    public String evaluar(@RequestParam long seccionId, @RequestParam long docenteId, @RequestParam int puntuacion, @RequestParam(required = false) String comentario, HttpSession s, RedirectAttributes ra) {
        try {
            long sid = student(s);
            long n = Long.parseLong(String.valueOf(dao.consultar("SELECT COUNT(*) n FROM Matriculas WHERE estudiante_id=? AND seccion_id=? AND estado='ACTIVA'", sid, seccionId).get(0).get("n")));
            if (n == 0) throw new IllegalArgumentException("No puede evaluar una sección que no cursa.");
            dao.ejecutarSQL("INSERT INTO EvaluacionesDocente(estudiante_id,docente_id,seccion_id,puntuacion,comentario) VALUES(?,?,?,?,?)", sid, docenteId, seccionId, puntuacion, comentario);
            ra.addFlashAttribute("ok", "Evaluación registrada. Sus respuestas serán confidenciales.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/estudiante/soporte";
    }

    @PostMapping("/foto")
    public String foto(@RequestParam MultipartFile archivo, HttpSession s, RedirectAttributes ra) {
        try {
            String fname = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename().toLowerCase();
            String ext = fname.endsWith(".png") ? "png" : (fname.endsWith(".jpeg") ? "jpeg" : "jpg");
            String ref = files.save(archivo, "perfiles", ext);
            dao.ejecutarSQL("INSERT INTO PerfilArchivos(usuario_id,foto_url) VALUES(?,?) ON CONFLICT(usuario_id) DO UPDATE SET foto_url=excluded.foto_url,actualizado_en=CURRENT_TIMESTAMP", user(s), ref);
            ra.addFlashAttribute("ok", "Fotografía actualizada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/estudiante/documentos";
    }

    private void notifyAdmins(String title, String msg, String enlace) {
        for (Map<String, Object> a : dao.consultar("SELECT id FROM Usuarios WHERE UPPER(rol)='ADMIN'")) {
            dao.ejecutarSQL("INSERT INTO Notificaciones(usuario_id,titulo,mensaje,enlace) VALUES(?,?,?,?)", a.get("id"), title, msg, enlace);
        }
    }
}
