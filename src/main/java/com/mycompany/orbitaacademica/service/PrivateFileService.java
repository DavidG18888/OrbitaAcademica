package com.mycompany.orbitaacademica.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class PrivateFileService {
    @Value("${orbita.storage.path:./private-uploads}") private String root;
    public String save(MultipartFile file, String folder, String requiredExtension) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Seleccione un archivo.");
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("El archivo supera 10 MB.");
        String name = file.getOriginalFilename() == null ? "archivo" : file.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ext.equals(requiredExtension)) throw new IllegalArgumentException("Solo se permite archivo ." + requiredExtension + ".");
        if ("pdf".equals(ext) && (file.getContentType() == null || !file.getContentType().toLowerCase().contains("pdf")))
            throw new IllegalArgumentException("El archivo no parece ser un PDF válido.");
        if ("jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext)) {
            String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
            if (!ct.startsWith("image/")) throw new IllegalArgumentException("Formato de imagen no válido.");
        }
        String key = UUID.randomUUID() + "." + ext;
        Path dir = Paths.get(root).toAbsolutePath().normalize().resolve(folder).normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(key).normalize();
        if (!target.startsWith(dir)) throw new SecurityException("Ruta no permitida.");
        file.transferTo(target);
        return folder + "/" + key;
    }
    public Path resolve(String key) {
        Path base = Paths.get(root).toAbsolutePath().normalize();
        Path p = base.resolve(key).normalize();
        if (!p.startsWith(base)) throw new SecurityException("Archivo no permitido.");
        return p;
    }
}
