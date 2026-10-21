package eu.amadeos.apps.writepad.service.format.odt;

import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.DocumentFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** OpenDocument text, LibreOffice's format and amade-writepad's default. */
public final class OdtFormat implements DocumentFormat {
    @Override
    public String name() {
        return "OpenDocument Text";
    }

    @Override
    public List<String> extensions() {
        return List.of("odt");
    }

    @Override
    public boolean keepsFormatting() {
        return true;
    }

    /**
     * A ZIP file whose first entry is {@code mimetype}, stored, with the type of ODF text. DOCX files are ZIP
     * files too, so the type has to be checked.
     */
    @Override
    public boolean recognizes(byte[] head) {
        byte[] name = "mimetype".getBytes(StandardCharsets.US_ASCII);
        byte[] type = Odf.MIME_TYPE.getBytes(StandardCharsets.US_ASCII);
        if (head.length < 30 + name.length + type.length) return false;
        if (head[0] != 'P' || head[1] != 'K' || head[2] != 3 || head[3] != 4) return false;
        int nameLength = (head[26] & 0xFF) | (head[27] & 0xFF) << 8;
        int extraLength = (head[28] & 0xFF) | (head[29] & 0xFF) << 8;
        if (nameLength != name.length || !startsWith(head, 30, name)) return false;
        return startsWith(head, 30 + nameLength + extraLength, type);
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
        if (offset < 0 || bytes.length - offset < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[offset + i] != prefix[i]) return false;
        }
        return true;
    }

    @Override
    public DocumentContent read(byte[] bytes) throws IOException {
        return OdtReader.read(bytes);
    }

    @Override
    public byte[] write(DocumentContent content) throws IOException {
        return OdtWriter.write(content);
    }
}
