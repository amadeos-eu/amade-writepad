package eu.amadeos.apps.writepad.ui.editor;

import eu.amadeos.apps.writepad.domain.Picture;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.picture.PictureAttributes;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cut, copy, paste and drop for the editor. Other programs get plain text in which a line break within a
 * paragraph ({@link WritepadDocument#LINE_BREAK}) is an ordinary line end, as LibreOffice and Word do it;
 * Swing would hand them the Unicode line separator, which most programs show as nothing or as a box. Within
 * this program the text keeps its breaks and its pictures, so a copied address block pastes as one paragraph
 * again and a copied picture as a picture.
 *
 * <p>Pictures also come in from other programs: an image on the clipboard (a screenshot) or image files
 * dropped from the file manager become pictures in the text. A single selected picture goes to other
 * programs as an image.
 */
public final class EditorTransferHandler extends TransferHandler {
    /** The text as it is in the document, line breaks and pictures included; offered only inside this JVM. */
    static final DataFlavor WRITEPAD_TEXT = localFlavor();
    private static final String IMAGE_EXTENSIONS = "png jpg jpeg gif bmp wbmp tif tiff";

    private Position exportStart;
    private Position exportEnd;

    /** Text with the pictures in it, by their index in the text. */
    record Fragment(String text, Map<Integer, Picture> pictures) {
    }

    private static DataFlavor localFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + Fragment.class.getName(),
                    "amade-writepad text", EditorTransferHandler.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The plain text other programs get for text of the document: each line break becomes a line end, and the
     * stand-ins of pictures are left out.
     */
    public static String plainText(String text) {
        return text.replace(WritepadDocument.LINE_BREAK, '\n').replace(String.valueOf(PictureAttributes.OBJECT), "");
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
            Map<Integer, Picture> pictures = new LinkedHashMap<>();
            if (document instanceof StyledDocument styled) {
                for (int i = start; i < end; i++) {
                    Picture p = PictureAttributes.at(styled, i);
                    if (p != null) pictures.put(i - start, p);
                }
            }
            return new TextTransferable(new Fragment(document.getText(start, end - start), pictures));
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
        return support.isDataFlavorSupported(WRITEPAD_TEXT) || support.isDataFlavorSupported(DataFlavor.stringFlavor)
                || support.isDataFlavorSupported(DataFlavor.imageFlavor)
                || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;
        JTextComponent editor = (JTextComponent) support.getComponent();
        Transferable data = support.getTransferable();
        try {
            if (support.isDataFlavorSupported(WRITEPAD_TEXT)) {
                Fragment fragment = (Fragment) data.getTransferData(WRITEPAD_TEXT);
                if (!placeDrop(support, editor)) return false;
                group(editor, () -> pasteFragment(editor, fragment));
                return true;
            }
            List<Picture> pictures = pictures(support, data);
            if (!pictures.isEmpty()) {
                if (!placeDrop(support, editor)) return false;
                group(editor, () -> {
                    for (Picture p : pictures) insertPicture(editor, p);
                });
                return true;
            }
            if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) return false;
            String text = ((String) data.getTransferData(DataFlavor.stringFlavor))
                    .replace("\r\n", "\n").replace('\r', '\n');
            if (!placeDrop(support, editor)) return false;
            editor.replaceSelection(text);
            return true;
        } catch (UnsupportedFlavorException | IOException e) {
            return false;
        }
    }

    /** For a drop, puts the caret where the drop goes; false for a drop into the dragged text itself. */
    private boolean placeDrop(TransferSupport support, JTextComponent editor) {
        if (!support.isDrop()) return true;
        int at = ((JTextComponent.DropLocation) support.getDropLocation()).getIndex();
        if (exportStart != null && at >= exportStart.getOffset() && at <= exportEnd.getOffset()) {
            exportStart = null;
            exportEnd = null;
            return false;
        }
        editor.setCaretPosition(at);
        return true;
    }

    private static void group(JTextComponent editor, Runnable change) {
        if (editor instanceof PageEditor page) page.groupEdit(change);
        else change.run();
    }

    /** Pastes text from this program: the text first, then each picture back onto its stand-in. */
    private static void pasteFragment(JTextComponent editor, Fragment fragment) {
        int start = editor.getSelectionStart();
        editor.replaceSelection(fragment.text());
        if (!(editor.getDocument() instanceof StyledDocument doc)) return;
        for (Map.Entry<Integer, Picture> p : fragment.pictures().entrySet()) {
            // A copy: two equal pictures side by side would share one element.
            PictureAttributes.replace(doc, start + p.getKey(), p.getValue().withDescription(p.getValue().description()));
        }
    }

    /** Pictures from the clipboard or a drop: an image, or image files. Empty if there are none. */
    private static List<Picture> pictures(TransferSupport support, Transferable data) {
        List<Picture> pictures = new ArrayList<>();
        try {
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                for (Object f : (List<?>) data.getTransferData(DataFlavor.javaFileListFlavor)) {
                    if (f instanceof File file && isImageFile(file)) {
                        pictures.add(Picture.of(Files.readAllBytes(file.toPath()), file.getName()));
                    }
                }
            } else if (support.isDataFlavorSupported(DataFlavor.imageFlavor)
                    && !support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Image image = (Image) data.getTransferData(DataFlavor.imageFlavor);
                pictures.add(Picture.of(toBuffered(image), "picture.png"));
            }
        } catch (UnsupportedFlavorException | IOException | RuntimeException e) {
            return List.of();
        }
        return pictures;
    }

    private static boolean isImageFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return file.isFile() && dot > 0 && IMAGE_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private static BufferedImage toBuffered(Image image) {
        if (image instanceof BufferedImage b) return b;
        BufferedImage b = new BufferedImage(Math.max(1, image.getWidth(null)), Math.max(1, image.getHeight(null)),
                BufferedImage.TYPE_INT_ARGB);
        b.createGraphics().drawImage(image, 0, 0, null);
        return b;
    }

    private static void insertPicture(JTextComponent editor, Picture picture) {
        Picture fitted = editor instanceof PageEditor page ? PictureEditing.fitted(picture, page.pageLayout()) : picture;
        try {
            PictureEditing.insert(editor, fitted);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The document's text for this program, plain text with line ends for all others, and a single picture as
     * an image.
     */
    private static final class TextTransferable implements Transferable {
        private final Fragment fragment;
        private final DataFlavor[] flavors;

        TextTransferable(Fragment fragment) {
            this.fragment = fragment;
            boolean onePicture = fragment.text().length() == 1 && fragment.pictures().size() == 1;
            flavors = onePicture ? new DataFlavor[]{WRITEPAD_TEXT, DataFlavor.imageFlavor}
                    : new DataFlavor[]{WRITEPAD_TEXT, DataFlavor.stringFlavor};
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return flavors.clone();
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            for (DataFlavor f : flavors) {
                if (f.equals(flavor)) return true;
            }
            return false;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            if (WRITEPAD_TEXT.equals(flavor)) return fragment;
            if (DataFlavor.imageFlavor.equals(flavor)) {
                BufferedImage image = fragment.pictures().values().iterator().next().image();
                if (image == null) throw new IOException("The picture cannot be decoded");
                return image;
            }
            return plainText(fragment.text());
        }
    }
}
