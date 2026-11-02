package eu.amadeos.apps.writepad.service.layout;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.Picture;
import eu.amadeos.apps.writepad.service.picture.PictureAttributes;
import eu.amadeos.apps.writepad.ui.editor.PageEditor;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PictureLayoutTest {
    static final String WORDS = "The quick brown fox jumps over the lazy dog and runs into the forest. ".repeat(12);

    static Picture picture(int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return Picture.of(image, "blue.png");
    }

    /** Lays out a document of a picture and text; returns the editor after layout. */
    private static PageEditor editor(Picture picture) throws Exception {
        AtomicReference<PageEditor> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                PageEditor editor = new PageEditor(new PageLayout(DocumentSettings.DEFAULT, 1));
                StyledDocument doc = editor.getStyledDocument();
                PictureAttributes.insert(doc, 0, picture);
                doc.insertString(doc.getLength(), WORDS, new SimpleAttributeSet());
                editor.setSize(editor.getPreferredSize());
                editor.layOut();
                editor.setSize(editor.getPreferredSize());
                editor.layOut();
                ref.set(editor);
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
        return ref.get();
    }

    private static Rectangle2D caret(PageEditor editor, int offset) throws Exception {
        AtomicReference<Rectangle2D> r = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                r.set(editor.modelToView2D(offset));
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
        return r.get();
    }

    @Test
    void inlinePictureIsAsWideAsItsSize() throws Exception {
        PageEditor editor = editor(picture(200, 100));
        Rectangle2D before = caret(editor, 0);
        Rectangle2D after = caret(editor, 1);
        assertEquals(150, after.getX() - before.getX(), 1.5, "200 px at 96 dpi are 150 pt");
        assertTrue(editor.section().floatingPictures().isEmpty());
    }

    @Test
    void textFlowsRightOfALeftPicture() throws Exception {
        Picture p = picture(200, 200).withPlacement(Picture.Placement.FLOAT_LEFT);
        PageEditor editor = editor(p);
        PagedSectionView section = editor.section();
        assertEquals(1, section.floatingPictures().size());
        Rectangle2D box = section.floatingPictures().getFirst().box();
        double textLeft = PageLayout.GAP + editor.pageLayout().left();
        assertEquals(textLeft, box.getX(), 0.5);
        // The first line starts right of the picture, a line below it at the margin again.
        Rectangle2D first = caret(editor, 1);
        assertTrue(first.getX() >= textLeft + 150 + FloatingPicture.SIDE_GAP - 1, "first line beside: " + first);
        int last = editor.getDocument().getLength() - 1;
        Rectangle2D end = caret(editor, last);
        assertTrue(end.getY() > box.getMaxY(), "text continues below the picture");
        boolean backAtMargin = false;
        for (int i = 1; i < last; i++) {
            Rectangle2D c = caret(editor, i);
            if (c.getY() > box.getMaxY() + FloatingPicture.GAP_BELOW && Math.abs(c.getX() - textLeft) < 1) {
                backAtMargin = true;
                break;
            }
        }
        assertTrue(backAtMargin, "a line below the picture starts at the margin");
    }

    @Test
    void textFlowsLeftOfARightPicture() throws Exception {
        Picture p = picture(200, 200).withPlacement(Picture.Placement.FLOAT_RIGHT);
        PageEditor editor = editor(p);
        Rectangle2D box = editor.section().floatingPictures().getFirst().box();
        double right = PageLayout.GAP + editor.pageLayout().left() + editor.pageLayout().contentWidth();
        assertEquals(right, box.getMaxX(), 0.5);
        for (int i = 1; i < 60; i++) {
            Rectangle2D c = caret(editor, i);
            if (c.getY() < box.getMaxY()) assertTrue(c.getX() <= box.getX() - FloatingPicture.SIDE_GAP + 1);
        }
    }
}
