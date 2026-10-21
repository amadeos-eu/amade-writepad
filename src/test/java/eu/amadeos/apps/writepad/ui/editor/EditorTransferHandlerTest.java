package eu.amadeos.apps.writepad.ui.editor;

import eu.amadeos.apps.writepad.OnEdt;
import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.layout.PageLayout;
import org.junit.jupiter.api.Test;

import javax.swing.TransferHandler;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Line breaks within paragraphs on the clipboard: line ends for other programs, kept within this one. */
class EditorTransferHandlerTest {
    private static final char BREAK = WritepadDocument.LINE_BREAK;
    private static final String ADDRESS = "Anna Muster" + BREAK + "Hauptstraße 1" + BREAK + "12345 Musterstadt\nNext";

    private PageEditor editor;
    private final Clipboard clipboard = new Clipboard("test");

    private void withAddress(Runnable test) throws Exception {
        OnEdt.run(() -> {
            editor = new PageEditor(new PageLayout(DocumentSettings.DEFAULT, 1.0));
            try {
                editor.getDocument().insertString(0, ADDRESS, null);
            } catch (BadLocationException e) {
                throw new AssertionError(e);
            }
            test.run();
        });
    }

    private String text() {
        try {
            return editor.getDocument().getText(0, editor.getDocument().getLength());
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    private Object clipboardData(DataFlavor flavor) {
        try {
            return clipboard.getContents(null).getTransferData(flavor);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void paste(Transferable data) {
        TransferHandler handler = editor.getTransferHandler();
        assertTrue(handler.importData(new TransferHandler.TransferSupport(editor, data)));
    }

    private int paragraphs() {
        Element root = editor.getDocument().getDefaultRootElement();
        return root.getElementCount();
    }

    @Test
    void otherProgramsGetLineEnds() throws Exception {
        withAddress(() -> {
            editor.select(0, ADDRESS.indexOf('\n'));
            editor.getTransferHandler().exportToClipboard(editor, clipboard, TransferHandler.COPY);
            assertEquals("Anna Muster\nHauptstraße 1\n12345 Musterstadt", clipboardData(DataFlavor.stringFlavor));
        });
    }

    @Test
    void pastingWithinTheProgramKeepsTheBreaks() throws Exception {
        withAddress(() -> {
            int end = ADDRESS.indexOf('\n');
            editor.select(0, end);
            editor.getTransferHandler().exportToClipboard(editor, clipboard, TransferHandler.COPY);
            editor.setCaretPosition(editor.getDocument().getLength());
            paste(clipboard.getContents(null));
            assertEquals(ADDRESS + ADDRESS.substring(0, end), text());
            assertEquals(2, paragraphs());
            int pastedBreak = ADDRESS.length() + ADDRESS.indexOf(BREAK);
            Element element = editor.getStyledDocument().getCharacterElement(pastedBreak);
            assertTrue(WritepadDocument.isLineBreak(element.getAttributes()), "pasted break has its own element");
        });
    }

    @Test
    void cutRemovesTheText() throws Exception {
        withAddress(() -> {
            editor.select(0, ADDRESS.indexOf(BREAK) + 1);
            editor.getTransferHandler().exportToClipboard(editor, clipboard, TransferHandler.MOVE);
            assertEquals("Anna Muster\n", clipboardData(DataFlavor.stringFlavor));
            assertEquals(ADDRESS.substring(ADDRESS.indexOf(BREAK) + 1), text());
        });
    }

    @Test
    void textFromOtherProgramsPastesWithParagraphs() throws Exception {
        withAddress(() -> {
            editor.setCaretPosition(0);
            paste(new StringSelection("one\r\ntwo\rthree\n"));
            assertTrue(text().startsWith("one\ntwo\nthree\nAnna"), text());
            assertEquals(5, paragraphs());
        });
    }
}
