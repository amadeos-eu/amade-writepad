package eu.amadeos.writepad.service;

import com.google.inject.Inject;
import eu.amadeos.writepad.repository.DocumentFiles;
import eu.amadeos.writepad.service.format.DocumentContent;
import eu.amadeos.writepad.service.format.DocumentFormat;
import eu.amadeos.writepad.service.format.FormatRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * Opens and saves documents. Disk access and parsing run on the background executor; the futures complete
 * there, so callers switch to the event dispatch thread themselves.
 */
public class DocumentService {
    private final DocumentFiles files;
    private final FormatRegistry formats;
    private final ExecutorService executor;

    @Inject
    public DocumentService(DocumentFiles files, FormatRegistry formats, ExecutorService executor) {
        this.files = files;
        this.formats = formats;
        this.executor = executor;
    }

    /** A document read from a file, and the format it was read in (which saving uses again). */
    public record Opened(Path path, DocumentFormat format, DocumentContent content) {
    }

    public FormatRegistry formats() {
        return formats;
    }

    /** Reads and parses the file; fails with the {@link IOException} if it cannot be read or understood. */
    public CompletableFuture<Opened> open(Path path) {
        Objects.requireNonNull(path, "path");
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] bytes = files.read(path);
                DocumentFormat format = formats.forOpening(path, bytes);
                return new Opened(path, format, format.read(bytes));
            } catch (IOException e) {
                throw new CompletionException(e);
            } catch (RuntimeException e) {
                // A parser bug must not look like a hang: report it as a file that cannot be read.
                throw new CompletionException(new IOException("Cannot read " + path.getFileName() + ": " + e, e));
            }
        }, executor);
    }

    /**
     * Writes bytes that a format produced (on the event dispatch thread, from the editor's document) to the
     * file.
     */
    public CompletableFuture<Void> write(Path path, byte[] bytes) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(bytes, "bytes");
        return CompletableFuture.runAsync(() -> {
            try {
                files.write(path, bytes);
            } catch (IOException e) {
                throw new CompletionException(e);
            } catch (UncheckedIOException e) {
                throw new CompletionException(e.getCause());
            }
        }, executor);
    }

    /** The cause of a failed future, unwrapped, for messages. */
    public static Throwable cause(Throwable failure) {
        Throwable t = failure;
        while (t instanceof CompletionException && t.getCause() != null) t = t.getCause();
        return t;
    }
}
