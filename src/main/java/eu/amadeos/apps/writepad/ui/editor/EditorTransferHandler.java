package eu.amadeos.apps.writepad.ui.editor;

import eu.amadeos.apps.writepad.service.format.WritepadDocument;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * Cut, copy, paste and drop for the editor. Other programs get plain text in which a line break within a
 * paragraph ({@link WritepadDocument#LINE_BREAK}) is an ordinary line end, as LibreOffice and Word do it;
 * Swing would hand them the Unicode line separator, which most programs show as nothing or as a box. Within
 * this program the text keeps its breaks, so a copied address block pastes as one paragraph again.
 */
public final class EditorTransferHandler extends TransferHandler {
    /** The text as it is in the document, line breaks included; offered only inside this JVM. */
    static final DataFlavor WRITEPAD_TEXT = localFlavor();

    private Position exportStart;
    private Position exportEnd;

    private static DataFlavor localFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=java.lang.String",
                    "amade-writepad text", EditorTransferHandler.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The plain text other programs get for text of the document: each line break becomes a line end. */
    public static String plainText(String text) {
        return text.replace(WritepadDocument.LINE_BREAK, '\n');
    }

    @Override
    public int getSourceActions(JComponent c) {
        return c instanceof JTextComponent text && text.isEditable() ? COPY_OR_MOVE : COPY;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JTextComponent editor = (JTextComponent) c;
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start == end) return null;
        Document document = editor.getDocument();
        try {
            exportStart = document.createPosition(start);
            exportEnd = document.createPosition(end);
            return new TextTransferable(document.getText(start, end - start));
        } catch (BadLocationException e) {
            return null;
        }
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        if (action == MOVE && data != null && exportStart != null && source instanceof JTextComponent editor
                && editor.isEditable()) {
            int start = exportStart.getOffset();
            int end = exportEnd.getOffset();
            try {
                if (end > start) editor.getDocument().remove(start, end - start);
            } catch (BadLocationException ignored) {
                // The text changed in a way that left nothing to remove.
            }
        }
        exportStart = null;
        exportEnd = null;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (!(support.getComponent() instanceof JTextComponent editor) || !editor.isEditable()
                || !editor.isEnabled()) {
            return false;
        }
        return support.isDataFlavorSupported(WRITEPAD_TEXT) || support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;
        JTextComponent editor = (JTextComponent) support.getComponent();
        String text;
        try {
            Transferable data = support.getTransferable();
            if (support.isDataFlavorSupported(WRITEPAD_TEXT)) {
                text = (String) data.getTransferData(WRITEPAD_TEXT);
            } else {
                text = ((String) data.getTransferData(DataFlavor.stringFlavor))
                        .replace("\r\n", "\n").replace('\r', '\n');
            }
        } catch (UnsupportedFlavorException | IOException e) {
            return false;
        }
        if (support.isDrop()) {
            int at = ((JTextComponent.DropLocation) support.getDropLocation()).getIndex();
            // Dropping inside the dragged text itself changes nothing.
            if (exportStart != null && at >= exportStart.getOffset() && at <= exportEnd.getOffset()) {
                exportStart = null;
                exportEnd = null;
                return false;
            }
            editor.setCaretPosition(at);
        }
        editor.replaceSelection(text);
        return true;
    }

    /** The document's text for this program, plain text with line ends for all others. */
    private static final class TextTransferable implements Transferable {
        private static final DataFlavor[] FLAVORS = {WRITEPAD_TEXT, DataFlavor.stringFlavor};
        private final String text;

        TextTransferable(String text) {
            this.text = text;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return FLAVORS.clone();
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return WRITEPAD_TEXT.equals(flavor) || DataFlavor.stringFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (WRITEPAD_TEXT.equals(flavor)) return text;
            if (DataFlavor.stringFlavor.equals(flavor)) return plainText(text);
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
