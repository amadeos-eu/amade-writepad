package eu.amadeos.writepad.service.print;

import eu.amadeos.writepad.domain.DocumentSettings;
import eu.amadeos.writepad.domain.Orientation;
import eu.amadeos.writepad.domain.PaperSize;
import eu.amadeos.writepad.service.layout.PageLayout;
import eu.amadeos.writepad.ui.editor.PageEditor;
import eu.amadeos.writepad.ui.editor.SampleText;
import org.junit.jupiter.api.Test;

import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentPageableTest {
    @Test
    void portraitFormatIsTheWholeSheet() {
        PageFormat format = DocumentPageable.pageFormat(DocumentSettings.DEFAULT);
        assertEquals(PageFormat.PORTRAIT, format.getOrientation());
        assertEquals(PaperSize.A4.width(), format.getWidth(), 0.01);
        assertEquals(PaperSize.A4.height(), format.getHeight(), 0.01);
        assertEquals(0, format.getImageableX(), 0.01);
    }

    @Test
    void landscapeFormatTurnsTheSheet() {
        PageFormat format = DocumentPageable.pageFormat(
                DocumentSettings.DEFAULT.withPaper(PaperSize.A5).withOrientation(Orientation.LANDSCAPE));
        assertEquals(PageFormat.LANDSCAPE, format.getOrientation());
        assertEquals(PaperSize.A5.height(), format.getWidth(), 0.01);
        assertEquals(PaperSize.A5.width(), format.getHeight(), 0.01);
        // The paper stays in portrait; the printer turns the graphics.
        assertEquals(PaperSize.A5.width(), format.getPaper().getWidth(), 0.01);
    }

    @Test
    void printerGetsThePaperButNoSecondRotation() {
        PrintRequestAttributeSet attributes = PrintService.attributes(
                DocumentSettings.DEFAULT.withPaper(PaperSize.A5).withOrientation(Orientation.LANDSCAPE), "test");
        assertEquals(MediaSizeName.ISO_A5, attributes.get(javax.print.attribute.standard.Media.class));
        assertEquals(null, attributes.get(javax.print.attribute.standard.OrientationRequested.class));
    }

    @Test
    void everyPagePrintsTextAndNothingBeyond() throws Exception {
        AtomicReference<DocumentPageable> pageable = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PageEditor editor = new PageEditor(new PageLayout(DocumentSettings.DEFAULT, 1));
            SampleText.appendTo(editor.getStyledDocument(), 3);
            editor.setSize(editor.getPreferredSize());
            pageable.set(new DocumentPageable(editor.layOut()));
        });
        DocumentPageable pages = pageable.get();
        assertTrue(pages.getNumberOfPages() >= 2);
        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; i < pages.getNumberOfPages(); i++) {
                assertTrue(darkPixels(pages, i) > 1000, "page " + (i + 1) + " is empty");
            }
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            assertEquals(Printable.NO_SUCH_PAGE, pages.print(g, pages.getPageFormat(0), pages.getNumberOfPages()));
            g.dispose();
        });
    }

    private static int darkPixels(DocumentPageable pages, int index) {
        PageFormat format = pages.getPageFormat(index);
        BufferedImage image = new BufferedImage((int) format.getWidth(), (int) format.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        assertEquals(Printable.PAGE_EXISTS, pages.print(g, format, index));
        g.dispose();
        int dark = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFF) < 128) dark++;
            }
        }
        return dark;
    }
}
