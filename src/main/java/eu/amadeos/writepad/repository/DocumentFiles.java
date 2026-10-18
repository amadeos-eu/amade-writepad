package eu.amadeos.writepad.repository;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Reads and writes document files. Writing goes to a temporary file next to the target that then replaces
 * it, so a full disk or a crash never leaves half a document; an existing file keeps its permissions.
 */
public class DocumentFiles {
    /** Larger files are refused instead of filling the memory: no word processor document is this big. */
    public static final long MAX_SIZE = 256L * 1024 * 1024;

    public byte[] read(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_SIZE) throw new IOException("File too large: " + size + " bytes");
        return Files.readAllBytes(file);
    }

    public void write(Path file, byte[] bytes) throws IOException {
        Path target = file.toAbsolutePath();
        Path directory = target.getParent();
        Set<PosixFilePermission> permissions = existingPermissions(target);
        Path temp = Files.createTempFile(directory, "." + target.getFileName(), ".tmp");
        try {
            Files.write(temp, bytes);
            if (permissions != null) Files.setPosixFilePermissions(temp, permissions);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Set<PosixFilePermission> existingPermissions(Path file) {
        try {
            return Files.exists(file) ? Files.getPosixFilePermissions(file) : null;
        } catch (IOException | UnsupportedOperationException e) {
            return null;
        }
    }
}
