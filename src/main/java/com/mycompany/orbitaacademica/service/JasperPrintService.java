package com.mycompany.orbitaacademica.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class JasperPrintService {

    private static final Color NAVY = new Color(23, 54, 93);
    private static final Color BLUE_ACCENT = new Color(59, 110, 165);
    private static final Color ZEBRA = new Color(246, 249, 252);
    private static final Color BORDER = new Color(216, 224, 234);
    private static final Color TEXT_MUTED = new Color(100, 116, 139);

    public byte[] generar(String titulo, String subtitulo, List<Map<String, Object>> filas) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.LETTER.rotate(), 32, 32, 30, 30);
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    PdfContentByte cb = w.getDirectContent();
                    Font fFoot = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);
                    String fecha = Util.hoyFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                            new Phrase("Órbita Académica · Documento generado el " + fecha, fFoot),
                            d.leftMargin(), d.bottom() - 14, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                            new Phrase("Página " + w.getPageNumber(), fFoot),
                            d.getPageSize().getWidth() - d.rightMargin(), d.bottom() - 14, 0);
                }
            });

            doc.open();

            PdfPTable header = new PdfPTable(new float[]{1.2f, 4.8f});
            header.setWidthPercentage(100);
            header.setSpacingAfter(14);

            PdfPCell logoCell = new PdfPCell();
            logoCell.setBackgroundColor(NAVY);
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setPadding(12);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            try {
                ClassPathResource res = new ClassPathResource("static/img/logo-orbita.png");
                if (res.exists()) {
                    Image img = Image.getInstance(res.getInputStream().readAllBytes());
                    img.scaleToFit(110, 42);
                    logoCell.addElement(img);
                }
            } catch (Exception ignored) {}
            header.addCell(logoCell);

            PdfPCell titleCell = new PdfPCell();
            titleCell.setBackgroundColor(NAVY);
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setPadding(14);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Font fInst = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(175, 202, 232));
            Font fTit = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE);
            Font fSub = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, new Color(224, 235, 248));
            Paragraph p1 = new Paragraph("ÓRBITA ACADÉMICA · REPORTE OFICIAL", fInst);
            p1.setSpacingAfter(3);
            Paragraph p2 = new Paragraph(titulo != null ? titulo : "Listado Académico", fTit);
            p2.setSpacingAfter(4);
            Paragraph p3 = new Paragraph(subtitulo != null ? subtitulo : "", fSub);
            titleCell.addElement(p1);
            titleCell.addElement(p2);
            titleCell.addElement(p3);
            header.addCell(titleCell);
            doc.add(header);

            boolean conParciales = !filas.isEmpty() && (filas.get(0).containsKey("parcial1") || filas.get(0).containsKey("asist"));

            float[] widths = conParciales
                    ? new float[]{1.1f, 2.7f, 0.8f, 0.8f, 0.8f, 0.9f, 1.3f, 1.0f}
                    : new float[]{1.1f, 2.8f, 1.0f, 1.3f, 1.0f, 1.2f, 1.8f};

            PdfPTable table = new PdfPTable(widths);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);

            String[] cols = conParciales
                    ? new String[]{"Código", "Estudiante", "P1 /25", "P2 /35", "P3 /40", "Final", "Estado", "Asist."}
                    : new String[]{"Código", "Asignatura", "Sección", "Período", "Promedio", "Estado", "Docente"};

            Font fHead = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            for (String col : cols) {
                PdfPCell c = new PdfPCell(new Phrase(col, fHead));
                c.setBackgroundColor(BLUE_ACCENT);
                c.setBorderColor(BLUE_ACCENT);
                c.setPadding(8);
                c.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(c);
            }

            Font fCell = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(36, 50, 71));
            Font fBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, NAVY);

            if (filas.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("No hay registros disponibles para mostrar.", fCell));
                empty.setColspan(cols.length);
                empty.setPadding(14);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                empty.setBorderColor(BORDER);
                table.addCell(empty);
            } else {
                int idx = 0;
                for (Map<String, Object> f : filas) {
                    Color bg = (idx++ % 2 == 1) ? ZEBRA : Color.WHITE;
                    if (conParciales) {
                        addCell(table, str(f.get("codigo")), fBold, bg, Element.ALIGN_LEFT);
                        addCell(table, str(f.get("nombre") != null ? f.get("nombre") : f.get("asignatura")), fCell, bg, Element.ALIGN_LEFT);
                        addCell(table, valOrDash(f.get("parcial1")), fCell, bg, Element.ALIGN_CENTER);
                        addCell(table, valOrDash(f.get("parcial2")), fCell, bg, Element.ALIGN_CENTER);
                        addCell(table, valOrDash(f.get("parcial3")), fCell, bg, Element.ALIGN_CENTER);
                        addCell(table, valOrDash(f.get("promedio")), fBold, bg, Element.ALIGN_CENTER);
                        String est = str(f.get("estado") != null ? f.get("estado") : f.get("m_estado"));
                        addCell(table, est, fBold, bg, Element.ALIGN_CENTER);
                        Object as = f.get("asist");
                        addCell(table, as != null ? as + "%" : "—", fCell, bg, Element.ALIGN_CENTER);
                    } else {
                        addCell(table, str(f.get("codigo")), fBold, bg, Element.ALIGN_LEFT);
                        addCell(table, str(f.get("asignatura") != null ? f.get("asignatura") : f.get("nombre")), fCell, bg, Element.ALIGN_LEFT);
                        addCell(table, str(f.get("seccion")), fCell, bg, Element.ALIGN_CENTER);
                        addCell(table, str(f.get("periodo")), fCell, bg, Element.ALIGN_LEFT);
                        addCell(table, valOrDash(f.get("promedio")), fBold, bg, Element.ALIGN_CENTER);
                        addCell(table, str(f.get("estado") != null ? f.get("estado") : "ACTIVA"), fBold, bg, Element.ALIGN_CENTER);
                        addCell(table, str(f.get("docente")), fCell, bg, Element.ALIGN_LEFT);
                    }
                }
            }

            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage(), e);
        }
    }

    private void addCell(PdfPTable t, String text, Font font, Color bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setBackgroundColor(bg);
        c.setBorderColor(BORDER);
        c.setPadding(7);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(c);
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String valOrDash(Object o) {
        return (o == null || String.valueOf(o).isBlank()) ? "—" : String.valueOf(o);
    }
}
