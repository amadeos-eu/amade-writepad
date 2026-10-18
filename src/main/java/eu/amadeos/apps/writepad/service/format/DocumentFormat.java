package eu.amadeos.apps.writepad.service.format;

import java.io.IOException;
import java.util.List;

/**
 * A file format amade-writepad can open and save: RTF, plain text, later ODT and DOCX.
 *
 * <p>{@link #read} builds a new document that nothing displays yet, so it may run on a background thread.
 * {@link #write} reads a document that may be shown in the editor and must run on the event dispatch thread;
 * it only produces bytes, the caller writes them to disk.
 */
public interface DocumentFormat {
    /** Short name for messages and file filters, e.g. "Rich Text Format". */
    String name();

    /** File extensions in lower case without dot, the usual one first, e.g. {@code rtf}. */
    List<String> extensions();

    default String defaultExtension() {
        return extensions().get(0);
    }

    /** False if saving loses formatting (plain text), so the user is asked first. */
    boolean keepsFormatting();

    /**
     * True if the first bytes of a file show it is in this format, whatever its name; e.g. RTF files start
     * with <code>{&#92;rtf</code>. Formats without a signature answer false.
     */
    default boolean recognizes(byte[] head) {
        return false;
    }

    /**
     * @throws IOException if the bytes are not a document of this format
     */
    DocumentContent read(byte[] bytes) throws IOException;

    byte[] write(DocumentContent content) throws IOException;
}
