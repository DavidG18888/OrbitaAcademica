package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import com.mycompany.orbitaacademica.service.PrivateFileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
public class PrivateDownloadController {
    private final TursoDAO dao;
    private final PrivateFileService fs;

    public PrivateDownloadController(TursoDAO d, PrivateFileService f) {
        this.dao = d;
        this.fs = f;
    }

    @GetMapping("/archivo-privado/{tipo}/{id}")
    public ResponseEntity<?> download(@PathVariable String tipo, @PathVariable long id, HttpSession s) {
        try {
            Object rolObj = s.getAttribute("rol");
            Object usrObj = s.getAttribute("usuario");
            if (rolObj == null || usrObj == null) {
                return ResponseEntity.status(401).build();
            }
            String rol = String.valueOf(rolObj);
            String usuario = String.valueOf(usrObj);
            String ref = null;
            String name = "archivo";

            if ("documento".equals(tipo)) {
                var r = dao.consultar("SELECT d.archivo_url, d.nombre_archivo, e.usuario_id FROM DocumentosEstudiante d JOIN Estudiantes e ON e.id=d.estudiante_id WHERE d.id=?", id);
                if (r.isEmpty()) return ResponseEntity.notFound().build();
                if (!"ADMIN".equals(rol) && !esMismoUsuario(usuario, r.get(0).get("usuario_id"))) {
                    return ResponseEntity.status(403).build();
                }
                ref = str(r.get(0).get("archivo_url"));
                name = str(r.get(0).get("nombre_archivo"));
            } else if ("pago".equals(tipo)) {
                var r = dao.consultar("SELECT p.comprobante_url, p.nombre_archivo, e.usuario_id FROM PagosCuota p JOIN Estudiantes e ON e.id=p.estudiante_id WHERE p.id=?", id);
                if (r.isEmpty()) return ResponseEntity.notFound().build();
                if (!"ADMIN".equals(rol) && !esMismoUsuario(usuario, r.get(0).get("usuario_id"))) {
                    return ResponseEntity.status(403).build();
                }
                ref = str(r.get(0).get("comprobante_url"));
                name = str(r.get(0).get("nombre_archivo"));
            } else if ("matricula".equals(tipo)) {
                var r = dao.consultar("SELECT p.comprobante_url, p.nombre_archivo, e.usuario_id FROM PagosMatriculaPeriodo p JOIN Estudiantes e ON e.id=p.estudiante_id WHERE p.id=?", id);
                if (r.isEmpty()) return ResponseEntity.notFound().build();
                if (!"ADMIN".equals(rol) && !esMismoUsuario(usuario, r.get(0).get("usuario_id"))) {
                    return ResponseEntity.status(403).build();
                }
                ref = str(r.get(0).get("comprobante_url"));
                name = str(r.get(0).get("nombre_archivo"));
            } else if ("foto".equals(tipo)) {
                var r = dao.consultar("SELECT p.foto_url FROM PerfilArchivos p JOIN Usuarios u ON u.id=p.usuario_id WHERE u.usuario=?", usuario);
                if (r.isEmpty()) return ResponseEntity.notFound().build();
                ref = str(r.get(0).get("foto_url"));
                name = "perfil.jpg";
            } else if ("evento".equals(tipo)) {
                var r = dao.consultar("SELECT imagen_url FROM EventosAcademicos WHERE id=?", id);
                if (r.isEmpty() || r.get(0).get("imagen_url") == null) return ResponseEntity.notFound().build();
                ref = str(r.get(0).get("imagen_url"));
                name = "evento-" + id;
            } else if ("tarea".equals(tipo)) {
                var r = dao.consultar("SELECT archivo_url, nombre_archivo FROM TareasSeccion WHERE id=?", id);
                if (r.isEmpty() || r.get(0).get("archivo_url") == null) return ResponseEntity.notFound().build();
                ref = str(r.get(0).get("archivo_url"));
                name = str(r.get(0).get("nombre_archivo"));
            } else if ("entrega".equals(tipo)) {
                var r = dao.consultar("SELECT archivo_url, nombre_archivo FROM EntregasTarea WHERE id=?", id);
                if (r.isEmpty() || r.get(0).get("archivo_url") == null) return ResponseEntity.notFound().build();
                ref = str(r.get(0).get("archivo_url"));
                name = str(r.get(0).get("nombre_archivo"));
            } else {
                return ResponseEntity.badRequest().build();
            }

            if (ref == null || ref.isBlank()) return ResponseEntity.notFound().build();
            Path path = fs.resolve(ref);
            if (!Files.exists(path)) return ResponseEntity.notFound().build();

            String content = Files.probeContentType(path);
            if (content == null) {
                String lower = path.getFileName().toString().toLowerCase();
                if (lower.endsWith(".pdf")) content = "application/pdf";
                else if (lower.endsWith(".png")) content = "image/png";
                else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) content = "image/jpeg";
                else if (lower.endsWith(".webp")) content = "image/webp";
                else content = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(content))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(name == null || name.isBlank() ? "archivo" : name).build().toString())
                    .body(new FileSystemResource(path));
        } catch (Exception e) {
            return ResponseEntity.status(404).build();
        }
    }

    private boolean esMismoUsuario(String usuario, Object ownerId) {
        if (ownerId == null) return false;
        List<Map<String, Object>> u = dao.consultar("SELECT id FROM Usuarios WHERE usuario=?", usuario);
        return !u.isEmpty() && Objects.equals(String.valueOf(u.get(0).get("id")), String.valueOf(ownerId));
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
