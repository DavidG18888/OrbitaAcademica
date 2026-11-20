package com.mycompany.orbitaacademica.controller;

import com.mycompany.orbitaacademica.dao.TursoDAO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/admin")
public class CrudController {

    public record Campo(String n, String label, String tipo, String sql) {}
    public record Ent(String tabla, String titulo, String lista, List<Campo> campos) {}

    private static Campo t(String n, String l) { return new Campo(n, l, "text", null); }
    private static Campo k(String n, String l, String tipo) { return new Campo(n, l, tipo, null); }
    private static Campo s(String n, String l, String sql) { return new Campo(n, l, "sel", sql); }
    private static final Campo ESTADO = k("estado", "Estado", "estado");
    private static final String CARRERAS = "SELECT id, nombre FROM Carreras ORDER BY nombre";

    private static final Map<String, Ent> ENT = Map.of(
        "estudiantes", new Ent("Estudiantes", "Estudiantes",
            "SELECT e.id, e.codigo AS Codigo, e.nombre || ' ' || e.apellido AS Nombre, c.nombre AS Carrera, e.estado AS Estado FROM Estudiantes e LEFT JOIN Carreras c ON c.id = e.carrera_id ORDER BY e.id DESC",
            List.of(t("codigo", "Código"), t("nombre", "Nombre"), t("apellido", "Apellido"), s("carrera_id", "Carrera", CARRERAS), t("correo", "Correo"), t("telefono", "Teléfono"), ESTADO)),
        "docentes", new Ent("Docentes", "Docentes",
            "SELECT id, codigo AS Codigo, nombre || ' ' || apellido AS Nombre, especialidad AS Especialidad, estado AS Estado FROM Docentes ORDER BY id DESC",
            List.of(t("codigo", "Código"), t("nombre", "Nombre"), t("apellido", "Apellido"), t("especialidad", "Especialidad"), t("correo", "Correo"), t("telefono", "Teléfono"), ESTADO)),
        "carreras", new Ent("Carreras", "Carreras",
            "SELECT id, codigo AS Codigo, nombre AS Nombre, estado AS Estado FROM Carreras ORDER BY nombre",
            List.of(t("codigo", "Código"), t("nombre", "Nombre"), ESTADO)),
        "asignaturas", new Ent("Asignaturas", "Asignaturas",
            "SELECT a.id, a.codigo AS Codigo, a.nombre AS Nombre, a.uv AS UV, c.nombre AS Carrera, (SELECT COUNT(*) FROM Secciones x WHERE x.asignatura_id = a.id) AS Secciones FROM Asignaturas a LEFT JOIN Carreras c ON c.id = a.carrera_id ORDER BY a.codigo",
            List.of(t("codigo", "Código"), t("nombre", "Nombre"), k("uv", "Unidades valorativas", "number"), s("carrera_id", "Carrera", CARRERAS), ESTADO)),
        "periodos", new Ent("Periodos", "Períodos académicos",
            "SELECT id, nombre AS Nombre, fecha_inicio AS Inicio, fecha_fin AS Fin, 'L ' || COALESCE(costo_matricula, 0) AS Matricula, estado AS Estado FROM Periodos ORDER BY id DESC",
            List.of(t("nombre", "Nombre del período"), k("fecha_inicio", "Inicio", "date"), k("fecha_fin", "Fin", "date"), k("costo_matricula", "Costo matrícula reingreso (L)", "decimal"), ESTADO)),
        "secciones", new Ent("Secciones", "Secciones y horarios",
            "SELECT s.id, s.codigo AS Seccion, a.nombre AS Asignatura, COALESCE(s.modalidad,'Presencial') AS Jornada, d.nombre || ' ' || d.apellido AS Docente, p.nombre AS Periodo, "
            + "COALESCE((SELECT GROUP_CONCAT(COALESCE(h.dia_semana,h.fecha_especifica) || ' ' || h.hora_inicio || '–' || h.hora_fin, ' · ') FROM HorariosSeccion h WHERE h.seccion_id=s.id), s.dia, 'Por definir') AS Horarios, "
            + "s.aula AS Aula, s.cupo AS Cupo, (SELECT COUNT(*) FROM Matriculas m WHERE m.seccion_id = s.id AND m.estado = 'ACTIVA') AS Inscritos FROM Secciones s LEFT JOIN Asignaturas a ON a.id = s.asignatura_id LEFT JOIN Docentes d ON d.id = s.docente_id LEFT JOIN Periodos p ON p.id = s.periodo_id ORDER BY s.id DESC",
            List.of(t("codigo", "Código de sección"),
                s("asignatura_id", "Asignatura", "SELECT id, codigo || ' · ' || nombre AS nombre FROM Asignaturas ORDER BY codigo"),
                s("docente_id", "Docente", "SELECT id, nombre || ' ' || apellido AS nombre FROM Docentes ORDER BY apellido"),
                s("periodo_id", "Período", "SELECT id, nombre FROM Periodos ORDER BY id DESC"),
                t("aula", "Aula"), k("cupo", "Cupo", "number")))
    );

    private final TursoDAO dao;
    private final BCryptPasswordEncoder encoder;

    public CrudController(TursoDAO dao, BCryptPasswordEncoder encoder) {
        this.dao = dao;
        this.encoder = encoder;
    }

    @GetMapping("/{e}")
    public String listar(@PathVariable String e, Model m) {
        return vista(e, null, m);
    }

    @GetMapping("/{e}/{id}/editar")
    public String editar(@PathVariable String e, @PathVariable long id, Model m) {
        Ent en = ENT.get(e);
        if (en == null) return "redirect:/panel";
        List<Map<String, Object>> r = dao.consultar("SELECT * FROM " + en.tabla() + " WHERE id = ?", id);
        if ("secciones".equals(e) && !r.isEmpty()) {
            List<Map<String, Object>> hs = dao.consultar("SELECT dia_semana,fecha_especifica,hora_inicio,hora_fin,COALESCE(aula,'') AS aula FROM HorariosSeccion WHERE seccion_id=? ORDER BY id", id);
            StringJoiner j = new StringJoiner(";");
            for (Map<String, Object> h : hs) {
                j.add((h.get("fecha_especifica") == null ? h.get("dia_semana") : "FECHA:" + h.get("fecha_especifica")) + "|" + h.get("hora_inicio") + "|" + h.get("hora_fin") + "|" + h.get("aula"));
            }
            r.get(0).put("horarios", j.toString());
        }
        return vista(e, r.isEmpty() ? null : r.get(0), m);
    }

    @PostMapping("/{e}/guardar")
    public String guardar(@PathVariable String e, @RequestParam Map<String, String> p, RedirectAttributes ra) {
        Ent en = ENT.get(e);
        if (en == null) return "redirect:/panel";
        if ("secciones".equals(e)) return guardarSeccion(p, ra);

        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (Campo c : en.campos()) {
            String v = p.get(c.n());
            cols.add(c.n());
            if ("decimal".equals(c.tipo())) {
                vals.add(v == null || v.isBlank() ? 0.0 : Double.parseDouble(v.trim()));
            } else {
                vals.add(v == null || v.isBlank() ? null : v.trim());
            }
        }

        String id = p.get("id");
        try {
            if (id == null || id.isBlank()) {
                dao.ejecutarSQL("INSERT INTO " + en.tabla() + "(" + String.join(",", cols)
                        + ") VALUES(" + "?,".repeat(cols.size() - 1) + "?)", vals.toArray());
                crearUsuario(en, p);
            } else {
                vals.add(Long.parseLong(id));
                dao.ejecutarSQL("UPDATE " + en.tabla() + " SET " + String.join("=?,", cols) + "=? WHERE id = ?", vals.toArray());
            }
            ra.addFlashAttribute("ok", "Guardado correctamente.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "No se pudo guardar: " + ex.getMessage());
        }
        return "redirect:/admin/" + e;
    }

    private String guardarSeccion(Map<String, String> p, RedirectAttributes ra) {
        try {
            String id = p.get("id");
            String codigo = requerido(p, "codigo");
            long asignatura = Long.parseLong(requerido(p, "asignatura_id"));
            long docente = Long.parseLong(requerido(p, "docente_id"));
            long periodo = Long.parseLong(requerido(p, "periodo_id"));
            String modalidad = p.getOrDefault("modalidad", "Presencial").trim();
            if (modalidad.isBlank()) modalidad = "Presencial";
            String aula = p.getOrDefault("aula", "").trim();
            String fechaInicio = p.getOrDefault("fecha_inicio", "").trim();
            String fechaFin = p.getOrDefault("fecha_fin", "").trim();
            String matriculaInicio = p.getOrDefault("matricula_inicio", "").trim();
            String matriculaFin = p.getOrDefault("matricula_fin", "").trim();
            int cupo = Integer.parseInt(requerido(p, "cupo"));
            if (cupo < 1) throw new IllegalArgumentException("El cupo debe ser mayor que cero.");
            long actualId = id == null || id.isBlank() ? 0 : Long.parseLong(id);

            // Valida únicamente que el MISMO docente o la MISMA aula física no tengan cruce a la misma hora.
            // Permite crear múltiples secciones paralelas de la misma asignatura (ej. Lunes-Jueves y Sábado).
            validarConflictos(p.get("horarios"), docente, periodo, aula, actualId);

            if (id == null || id.isBlank()) {
                dao.ejecutarSQL("INSERT INTO Secciones(codigo,asignatura_id,docente_id,periodo_id,modalidad,aula,cupo,fecha_inicio,fecha_fin,matricula_inicio,matricula_fin,estado) VALUES(?,?,?,?,?,?,?,?,?,?,?, 'ACTIVA')",
                        codigo, asignatura, docente, periodo, modalidad, aula, cupo, vacioNull(fechaInicio), vacioNull(fechaFin), vacioNull(matriculaInicio), vacioNull(matriculaFin));
                List<Map<String, Object>> r = dao.consultar("SELECT id FROM Secciones WHERE codigo=? AND periodo_id=? ORDER BY id DESC LIMIT 1", codigo, periodo);
                long sid = Long.parseLong(String.valueOf(r.get(0).get("id")));
                guardarBloques(sid, p.get("horarios"));
            } else {
                long sid = Long.parseLong(id);
                dao.ejecutarSQL("UPDATE Secciones SET codigo=?,asignatura_id=?,docente_id=?,periodo_id=?,modalidad=?,aula=?,cupo=?,fecha_inicio=?,fecha_fin=?,matricula_inicio=?,matricula_fin=? WHERE id=?",
                        codigo, asignatura, docente, periodo, modalidad, aula, cupo, vacioNull(fechaInicio), vacioNull(fechaFin), vacioNull(matriculaInicio), vacioNull(matriculaFin), sid);
                dao.ejecutarSQL("DELETE FROM HorariosSeccion WHERE seccion_id=?", sid);
                guardarBloques(sid, p.get("horarios"));
            }
            ra.addFlashAttribute("ok", "Sección y horarios guardados correctamente.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "No se pudo guardar la sección: " + ex.getMessage());
        }
        return "redirect:/admin/secciones";
    }

    private Object vacioNull(String v) { return v == null || v.isBlank() ? null : v; }

    private String requerido(Map<String, String> p, String k) {
        String v = p.get(k);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Falta el campo " + k);
        return v.trim();
    }

    private void validarConflictos(String raw, long docenteId, long periodoId, String aulaGeneral, long seccionActual) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Agregue al menos un bloque de horario.");
        List<String[]> nuevos = new ArrayList<>();
        for (String b : raw.split(";")) {
            String[] v = b.split("\\|", -1);
            if (v.length < 4) throw new IllegalArgumentException("Formato de horario inválido.");
            nuevos.add(v);
        }
        for (int i = 0; i < nuevos.size(); i++) {
            String[] n = nuevos.get(i);
            for (int j = i + 1; j < nuevos.size(); j++) {
                String[] otro = nuevos.get(j);
                if (coincideDia(n[0], otro[0]) && seCruzan(n[1], n[2], otro[1], otro[2]))
                    throw new IllegalArgumentException("Dos bloques de esta sección se cruzan entre sí.");
            }
            String aulaNueva = n[3].isBlank() ? aulaGeneral : n[3];
            boolean esVirtual = aulaNueva.equalsIgnoreCase("Virtual") || aulaNueva.equalsIgnoreCase("En línea");
            List<Map<String, Object>> existentes = dao.consultar(
                    "SELECT h.dia_semana,h.fecha_especifica,h.hora_inicio,h.hora_fin,COALESCE(h.aula,s.aula,'') AS aula "
                    + "FROM HorariosSeccion h JOIN Secciones s ON s.id=h.seccion_id "
                    + "WHERE s.periodo_id=? AND s.id<>? AND COALESCE(s.estado,'ACTIVA')='ACTIVA' "
                    + "AND (s.docente_id=? OR (?<>'' AND ?='0' AND COALESCE(h.aula,s.aula,'')=?))",
                    periodoId, seccionActual, docenteId, aulaNueva, esVirtual ? "1" : "0", aulaNueva);
            for (Map<String, Object> e : existentes) {
                String diaViejo = e.get("dia_semana") == null ? "" : e.get("dia_semana").toString();
                String fechaVieja = e.get("fecha_especifica") == null ? "" : e.get("fecha_especifica").toString();
                boolean mismo = coincideDia(n[0], !fechaVieja.isBlank() ? "FECHA:" + fechaVieja : diaViejo);
                if (mismo && seCruzan(n[1], n[2], String.valueOf(e.get("hora_inicio")), String.valueOf(e.get("hora_fin"))))
                    throw new IllegalArgumentException("El docente o el aula física ya tiene otra sección en ese mismo horario.");
            }
        }
    }

    private boolean coincideDia(String a, String b) {
        boolean af = a.startsWith("FECHA:"), bf = b.startsWith("FECHA:");
        if (af && bf) return a.substring(6).equals(b.substring(6));
        if (!af && !bf) return a.equals(b);
        try {
            String fecha = af ? a.substring(6) : b.substring(6);
            String dia = af ? b : a;
            int num = java.time.LocalDate.parse(fecha).getDayOfWeek().getValue();
            return num == switch (dia) { case "Lunes" -> 1; case "Martes" -> 2; case "Miércoles" -> 3; case "Jueves" -> 4; case "Viernes" -> 5; case "Sábado" -> 6; case "Domingo" -> 7; default -> -1; };
        } catch (Exception ex) { return false; }
    }

    private boolean seCruzan(String inicioA, String finA, String inicioB, String finB) {
        return inicioA.compareTo(finB) < 0 && inicioB.compareTo(finA) < 0;
    }

    private void guardarBloques(long seccionId, String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Agregue al menos un bloque de horario.");
        Set<String> claves = new HashSet<>();
        for (String bloque : raw.split(";")) {
            String[] v = bloque.split("\\|", -1);
            if (v.length < 4) throw new IllegalArgumentException("Formato de horario inválido.");
            String dia = v[0].startsWith("FECHA:") ? null : v[0];
            String fecha = v[0].startsWith("FECHA:") ? v[0].substring(6) : null;
            String inicio = v[1], fin = v[2], aula = v[3];
            if (!inicio.matches("\\d{2}:\\d{2}") || !fin.matches("\\d{2}:\\d{2}") || inicio.compareTo(fin) >= 0)
                throw new IllegalArgumentException("Revise las horas de cada bloque.");
            if (dia != null && !List.of("Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo").contains(dia))
                throw new IllegalArgumentException("Día de la semana inválido.");
            String clave = v[0] + inicio + fin;
            if (!claves.add(clave)) throw new IllegalArgumentException("Hay bloques repetidos.");
            dao.ejecutarSQL("INSERT INTO HorariosSeccion(seccion_id,dia_semana,fecha_especifica,hora_inicio,hora_fin,aula) VALUES(?,?,?,?,?,?)",
                    seccionId, dia, fecha, inicio, fin, aula);
        }
    }

    @PostMapping("/{e}/{id}/eliminar")
    public String eliminar(@PathVariable String e, @PathVariable long id, RedirectAttributes ra) {
        Ent en = ENT.get(e);
        if (en == null) return "redirect:/panel";
        try {
            dao.ejecutarSQL("DELETE FROM " + en.tabla() + " WHERE id = ?", id);
            ra.addFlashAttribute("ok", "Registro eliminado.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "No se pudo eliminar el registro.");
        }
        return "redirect:/admin/" + e;
    }

    private void crearUsuario(Ent en, Map<String, String> p) {
        String rol = en.tabla().equals("Estudiantes") ? "ESTUDIANTE" : en.tabla().equals("Docentes") ? "DOCENTE" : null;
        if (rol == null) return;
        String cod = p.get("codigo").trim();
        try {
            dao.ejecutarSQL("INSERT INTO Usuarios(usuario,nombre,correo,clave,rol) VALUES(?,?,?,?,?)",
                    cod, p.get("nombre").trim() + " " + p.get("apellido").trim(), null, encoder.encode(cod), rol);
            dao.ejecutarSQL("UPDATE " + en.tabla() + " SET usuario_id = (SELECT id FROM Usuarios WHERE usuario = ?) WHERE codigo = ?", cod, cod);
        } catch (RuntimeException x) {
            dao.ejecutarSQL("DELETE FROM " + en.tabla() + " WHERE codigo = ?", cod);
            throw x;
        }
    }

    private String vista(String e, Map<String, Object> form, Model m) {
        Ent en = ENT.get(e);
        if (en == null) return "redirect:/panel";

        List<Map<String, Object>> filas = dao.consultar(en.lista());
        List<String> cols = filas.isEmpty() ? List.of()
                : filas.get(0).keySet().stream().filter(c -> !c.equals("id")).toList();

        Map<String, List<Map<String, Object>>> ops = new HashMap<>();
        for (Campo c : en.campos()) {
            if (c.sql() != null) ops.put(c.n(), dao.consultar(c.sql()));
        }

        m.addAttribute("slug", e);
        m.addAttribute("titulo", en.titulo());
        m.addAttribute("campos", en.campos());
        m.addAttribute("filas", filas);
        m.addAttribute("cols", cols);
        m.addAttribute("ops", ops);
        m.addAttribute("form", form);
        return "admin/crud";
    }
}
