package eu.amadeos.apps.writepad.service.format.doc;

import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.DocumentFormat;
import eu.amadeos.apps.writepad.service.format.LibreOffice;
import eu.amadeos.apps.writepad.service.format.docx.DocxFormat;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * The binary Word format of Word 97 to 2003, through LibreOffice: opening converts the file to DOCX and reads
 * that, saving writes DOCX and converts it to DOC. Without LibreOffice, such files cannot be opened and the
 * format is not offered for saving.
 */
public final class DocFormat implements DocumentFormat {
    /** The signature of OLE2 compound files, which DOC files are. */
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
            (byte) 0xE1};

    private final Optional<LibreOffice> libreOffice;
    private final DocxFormat docx = new DocxFormat();

    public DocFormat() {
        this(LibreOffice.find());
    }

    public DocFormat(Optional<LibreOffice> libreOffice) {
        this.libreOffice = libreOffice;
    }

    @Override
    public String name() {
        return "Word 97-2003 Document";
    }

    @Override
    public List<String> extensions() {
        return List.of("doc");
    }

    @Override
    public boolean keepsFormatting() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return libreOffice.isPresent();
    }

    @Override
    public boolean recognizes(byte[] head) {
        if (head.length < OLE2.length) return false;
        for (int i = 0; i < OLE2.length; i++) {
            if (head[i] != OLE2[i]) return false;
        }
        return true;
    }

    @Override
    public DocumentContent read(byte[] bytes) throws IOException {
        LibreOffice converter = libreOffice.orElseThrow(LibreOffice.MissingException::new);
        return docx.read(converter.convert(bytes, "doc", "docx:MS Word 2007 XML"));
    }

    /** DOCX; {@link #finish} turns it into DOC. */
    @Override
    public byte[] write(DocumentContent content) throws IOException {
        if (libreOffice.isEmpty()) throw new LibreOffice.MissingException();
        return docx.write(content);
    }

    @Override
    public byte[] finish(byte[] written) throws IOException {
        LibreOffice converter = libreOffice.orElseThrow(LibreOffice.MissingException::new);
        return converter.convert(written, "docx", "doc:MS Word 97");
    }
}
