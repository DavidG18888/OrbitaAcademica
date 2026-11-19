package com.mycompany.orbitaacademica.service;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

@Service
public class JasperPrintService {
    public byte[] generar(String titulo, String subtitulo, List<Map<String,Object>> filas) {
        try (InputStream in = new ClassPathResource("reports/orbita-listado.jrxml").getInputStream()) {
            JasperReport report = JasperCompileManager.compileReport(in);
            List<Map<String, ?>> data = new ArrayList<>();
            for (Map<String,Object> fila : filas) {
                Map<String,Object> row = new HashMap<>();
                for (String key : List.of("codigo","asignatura","seccion","periodo","docente","parcial1","parcial2","parcial3","promedio","estado","nombre","asist")) {
                    Object value = fila.get(key);
                    if (value == null && "estado".equals(key)) value = fila.get("m_estado");
                    if (value == null && "asist".equals(key)) {
                        Object asistidas = fila.get("asistidas");
                        Object clases = fila.get("clases");
                        value = asistidas == null ? "" : (clases == null ? asistidas : asistidas + "/" + clases);
                    }
                    row.put(key, value == null ? "" : String.valueOf(value));
                }
                data.add(row);
            }
            Map<String,Object> params = new HashMap<>();
            params.put("TITULO", titulo);
            params.put("SUBTITULO", subtitulo);
            if (data.isEmpty()) data.add(Map.of("codigo", "", "asignatura", "", "seccion", "", "periodo", "", "docente", "", "promedio", "", "estado", ""));
            JasperPrint print = JasperFillManager.fillReport(report, params, new JRMapCollectionDataSource(data));
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el reporte Jasper: " + e.getMessage(), e);
        }
    }
}
