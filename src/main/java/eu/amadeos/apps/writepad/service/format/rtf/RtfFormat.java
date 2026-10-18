package eu.amadeos.apps.writepad.service.format.rtf;

import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.DocumentFormat;

import java.io.IOException;
import java.util.List;

/** Rich Text Format, as written by WordPad, Word and LibreOffice. */
public final class RtfFormat implements DocumentFormat {
    @Override
    public String name() {
        return "Rich Text Format";
    }

    @Override
    public List<String> extensions() {
        return List.of("rtf");
    }

    @Override
    public boolean keepsFormatting() {
        return true;
    }

    /** <code>{&#92;rtf</code>, possibly after a UTF-8 byte order mark and white space. */
    @Override
    public boolean recognizes(byte[] head) {
        int i = 0;
        if (head.length >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) i = 3;
        while (i < head.length && Character.isWhitespace(head[i])) i++;
        byte[] signature = {'{', '\\', 'r', 't', 'f'};
        if (head.length - i < signature.length) return false;
        for (int k = 0; k < signature.length; k++) {
            if (head[i + k] != signature[k]) return false;
        }
        return true;
    }

    @Override
    public DocumentContent read(byte[] bytes) throws IOException {
        return RtfReader.read(bytes);
    }

    @Override
    public byte[] write(DocumentContent content) {
        return RtfWriter.write(content);
    }
}
