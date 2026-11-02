package fr.portfolio.helpdesk.ticket;

import fr.portfolio.helpdesk.common.ApiException;
import fr.portfolio.helpdesk.config.AppProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Stockage des pièces jointes sur disque, sous un nom aléatoire (jamais le nom fourni par le client). */
@Service
public class FileStorageService {

    static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf", "text/plain", "application/zip",
            "application/msword", "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final Path root;

    public FileStorageService(AppProperties props) {
        this.root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Aucun fichier reçu");
        if (!ALLOWED_TYPES.contains(file.getContentType())) throw ApiException.badRequest("Type de fichier non autorisé");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "") : "";
        String stored = UUID.randomUUID() + (ext.length() <= 6 ? ext : "");
        try {
            Files.copy(file.getInputStream(), root.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return stored;
    }

    public Resource load(String storedName) {
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root) || !Files.exists(p)) throw ApiException.notFound("Fichier introuvable");
        return new FileSystemResource(p);
    }
}
