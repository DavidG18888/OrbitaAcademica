package com.mycompany.orbitaacademica.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

// Utilidades académicas compartidas por los portales
public final class Util {

    public static final double PASA = 70;
    public static final ZoneId ZONA = ZoneId.of("America/Tegucigalpa");
    public static final List<String> DIAS = List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado");

    // Los parciales se almacenan como puntos obtenidos: 25 + 35 + 40. Extras limitados a 100.
    public static final String PROM = "CASE WHEN c.parcial1 IS NOT NULL AND c.parcial2 IS NOT NULL AND c.parcial3 IS NOT NULL "
            + "THEN MIN(100, ROUND(c.parcial1 + c.parcial2 + c.parcial3 + COALESCE(c.puntos_extra,0), 1)) END";

    private Util() {}

    public static Double num(Object o) {
        if (o == null) return null;
        try { return Double.valueOf(o.toString()); } catch (Exception e) { return null; }
    }

    public static LocalDate hoyFecha() { return LocalDate.now(ZONA); }

    public static String hoy() {
        return switch (hoyFecha().getDayOfWeek()) {
            case MONDAY -> "Lunes"; case TUESDAY -> "Martes"; case WEDNESDAY -> "Miércoles";
            case THURSDAY -> "Jueves"; case FRIDAY -> "Viernes"; case SATURDAY -> "Sábado"; default -> "Domingo";
        };
    }

    public static String fechaLarga() {
        return hoyFecha().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es", "HN")));
    }

    public static String saludo() {
        int h = LocalDateTime.now(ZONA).getHour();
        return h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches";
    }

    // Agrega estado, asistencia % y horario legible a cada fila
    public static void marcar(List<Map<String, Object>> rows) {
        for (Map<String, Object> r : rows) {
            Double p = num(r.get("promedio"));
            r.put("estado", p == null ? "Cursando" : p >= PASA ? "Aprobada" : "Reprobada");
            r.put("clase", p == null ? "neutral" : p >= PASA ? "ok" : "bad");
            Double cl = num(r.get("clases")), as = num(r.get("asistidas"));
            r.put("asist", cl == null || cl == 0 || as == null ? null : Math.round(100.0 * as / cl));
            Double cu = num(r.get("cupo")), in = num(r.get("inscritos"));
            r.put("ocup", cu == null || cu == 0 || in == null ? 0 : Math.min(100, Math.round(100.0 * in / cu)));
            Object d = r.get("dia");
            r.put("horario", d == null ? "Por definir" : d + " " + r.get("hora_inicio") + "–" + r.get("hora_fin"));
        }
    }

    // Agrupa clases por día de la semana (para la vista de horario)
    public static Map<String, List<Map<String, Object>>> semana(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> s = new LinkedHashMap<>();
        for (String d : DIAS) s.put(d, new ArrayList<>());
        for (Map<String, Object> r : rows) {
            List<Map<String, Object>> l = s.get(String.valueOf(r.get("dia")));
            if (l == null) continue;
            Map<String, Object> c = new LinkedHashMap<>(r);
            c.put("tono", Math.floorMod(String.valueOf(r.get("asignatura")).hashCode(), 360));
            l.add(c);
        }
        s.values().forEach(l -> l.sort(Comparator.comparing(x -> String.valueOf(x.get("hora_inicio")))));
        return s;
    }

    public static String csv(List<String> cab, List<List<Object>> filas) {
        StringBuilder sb = new StringBuilder("\uFEFF").append(String.join(",", cab)).append("\n");
        for (List<Object> f : filas) {
            sb.append(f.stream().map(Util::celda).collect(Collectors.joining(","))).append("\n");
        }
        return sb.toString();
    }

    private static String celda(Object o) {
        String s = o == null ? "" : o.toString();
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
