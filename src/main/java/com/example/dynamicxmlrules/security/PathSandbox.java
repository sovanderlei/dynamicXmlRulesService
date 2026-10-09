package com.example.dynamicxmlrules.security;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Resolve caminhos informados nos workflows sempre relativos a um diretório base,
 * bloqueando caminhos absolutos e tentativas de path traversal ({@code ../}).
 */
public final class PathSandbox {

    private PathSandbox() {
    }

    public static Path resolve(Path baseDir, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new WorkflowSecurityException("Caminho de arquivo vazio.");
        }
        Path requested;
        try {
            requested = Path.of(relativePath);
        } catch (InvalidPathException e) {
            throw new WorkflowSecurityException("Caminho de arquivo inválido: " + relativePath, e);
        }
        if (requested.isAbsolute() || relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            throw new WorkflowSecurityException("Caminhos absolutos não são permitidos: " + relativePath);
        }
        Path base = baseDir.toAbsolutePath().normalize();
        Path target = base.resolve(requested).normalize();
        if (!target.startsWith(base) || target.equals(base)) {
            throw new WorkflowSecurityException("Caminho fora do diretório permitido: " + relativePath);
        }
        return target;
    }
}
