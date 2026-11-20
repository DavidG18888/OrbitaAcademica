package com.mycompany.orbitaacademica.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

@Service
public class PrivateFileService {
    @Value("${orbita.storage.path:./private-uploads}")
    private String root;

    public String save(MultipartFile file, String folder, String... allowedExtensions) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Seleccione un archivo.");
        if (file.getSize() > 15L * 1024 * 1024) throw new IllegalArgumentException("El archivo supera el límite de 15 MB.");
        String name = file.getOriginalFilename() == null ? "archivo" : file.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase().replaceAll("[^a-z0-9]", "") : "bin";
        if (ext.isBlank()) ext = "bin";

        boolean allowAll = allowedExtensions == null || allowedExtensions.length == 0 || List.of(allowedExtensions).contains("*");
        if (!allowAll) {
            List<String> allowed = List.of(allowedExtensions);
            if (!allowed.contains(ext)) {
                throw new IllegalArgumentException("Formato no permitido. Se acepta: " + String.join(", ", allowed).toUpperCase() + ".");
            }
            if ("pdf".equals(ext) && (file.getContentType() == null || !file.getContentType().toLowerCase().contains("pdf"))) {
                throw new IllegalArgumentException("El archivo no parece ser un PDF válido.");
            }
            if (List.of("jpg", "jpeg", "png", "webp").contains(ext)) {
                String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
                if (!ct.startsWith("image/")) throw new IllegalArgumentException("Formato de imagen no válido.");
            }
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
