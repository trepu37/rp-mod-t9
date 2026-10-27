package ru.rpautocomplete;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Безопасное чтение/запись файлов в UTF-8. */
final class Io {
    private Io() {}

    /** .minecraft/config/rpautocomplete */
    static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("rpautocomplete");
    }

    static String read(Path p) throws IOException {
        String s = Files.readString(p, StandardCharsets.UTF_8);
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1); // файл, сохранённый Блокнотом с BOM
        }
        return s;
    }

    /** Запись через временный файл, чтобы при сбое не потерять данные. */
    static void write(Path p, String content) throws IOException {
        Files.createDirectories(p.getParent());
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
