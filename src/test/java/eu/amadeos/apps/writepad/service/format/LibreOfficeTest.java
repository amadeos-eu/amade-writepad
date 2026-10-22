package eu.amadeos.apps.writepad.service.format;

import eu.amadeos.commons.process.FinishedProcess;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The conversion without LibreOffice: a fake soffice writes the file the real one would. */
class LibreOfficeTest {
    private static final Path SOFFICE = Path.of("/usr/bin/soffice");

    @Test
    void convertsInAProfileOfItsOwnAndCleansUp() throws IOException {
        List<List<String>> commands = new ArrayList<>();
        LibreOffice office = LibreOffice.at(SOFFICE, command -> {
            commands.add(command);
            Path outdir = Path.of(command.get(command.indexOf("--outdir") + 1));
            Files.writeString(outdir.resolve("document.docx"), "converted");
            return FinishedProcess.printing("convert document.doc -> document.docx using filter : MS Word 2007 XML");
        });

        byte[] docx = office.convert("old".getBytes(), "doc", "docx:MS Word 2007 XML");

        assertArrayEquals("converted".getBytes(), docx);
        List<String> command = commands.getFirst();
        assertEquals(SOFFICE.toString(), command.getFirst());
        assertTrue(command.get(1).startsWith("-env:UserInstallation=file:"));
        assertEquals("docx:MS Word 2007 XML", command.get(command.indexOf("--convert-to") + 1));
        Path input = Path.of(command.getLast());
        assertEquals("document.doc", input.getFileName().toString());
        assertFalse(Files.exists(input.getParent()), "the temporary folder is deleted");
    }

    @Test
    void reportsWhatLibreOfficePrinted() {
        LibreOffice office = LibreOffice.at(SOFFICE,
                command -> FinishedProcess.failing(1, "Error: source file could not be loaded"));
        IOException e = assertThrows(IOException.class, () -> office.convert(new byte[0], "doc", "docx"));
        assertEquals("LibreOffice could not convert the document: Error: source file could not be loaded",
                e.getMessage());
    }

    @Test
    void anExitCodeOfZeroWithoutAFileIsAFailure() {
        LibreOffice office = LibreOffice.at(SOFFICE, command -> FinishedProcess.printing(""));
        IOException e = assertThrows(IOException.class, () -> office.convert(new byte[0], "doc", "docx"));
        assertEquals("LibreOffice could not convert the document", e.getMessage());
    }
}
