package eu.amadeos.apps.writepad.service.format.docx;

import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.DocumentFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Word's Office Open XML format, as written by Word since 2007 and by LibreOffice. */
public final class DocxFormat implements DocumentFormat {
    private static final byte[] MAIN_PART = "word/document.xml".getBytes(StandardCharsets.US_ASCII);

    @Override
    public String name() {
        return "Word Document";
    }

    @Override
    public List<String> extensions() {
        return List.of("docx");
    }

    @Override
    public boolean keepsFormatting() {
        return true;
    }

    /** A ZIP file with a {@code word/document.xml}; its name is stored uncompressed in the ZIP's directory. */
    @Override
    public boolean recognizes(byte[] head) {
        if (head.length < 4 || head[0] != 'P' || head[1] != 'K' || head[2] != 3 || head[3] != 4) return false;
        outer:
        for (int i = 0; i <= head.length - MAIN_PART.length; i++) {
            for (int k = 0; k < MAIN_PART.length; k++) {
                if (head[i + k] != MAIN_PART[k]) continue outer;
            }
            return true;
        }
        return false;
    }

    @Override
    public DocumentContent read(byte[] bytes) throws IOException {
        return DocxReader.read(bytes);
    }

    @Override
    public byte[] write(DocumentContent content) throws IOException {
        return DocxWriter.write(content);
    }
}
