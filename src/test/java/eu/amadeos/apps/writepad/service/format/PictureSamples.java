package eu.amadeos.apps.writepad.service.format;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.Picture;
import eu.amadeos.apps.writepad.service.picture.PictureAttributes;

import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A document with a picture in the line and a floating one, and the checks every format's round trip makes. */
public final class PictureSamples {
    private PictureSamples() {
    }

    public static Picture picture(Color color, int width, int height, String name) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        try {
            return Picture.of(image, name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** "Before [red picture] after." and a paragraph with a blue picture floating left. */
    public static DocumentContent document() {
        StyledDocument doc = Documents.newDocument();
        try {
            doc.insertString(0, "Before  after.\nFloating text beside the blue picture.", null);
            PictureAttributes.insert(doc, 7, picture(Color.RED, 40, 20, "red.png").withSize(30, 15)
                    .withDescription("A red bar"));
            int floatAt = "Before X after.\n".length();
            PictureAttributes.insert(doc, floatAt, picture(Color.BLUE, 64, 64, "blue.png").withSize(72, 72)
                    .withPlacement(Picture.Placement.FLOAT_LEFT).withBorder(Picture.Border.THIN)
                    .withDescription("A blue square"));
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        return new DocumentContent(doc, DocumentSettings.DEFAULT);
    }

    /** Checks what a format kept of {@link #document()}; {@code floats} is false for formats that keep none. */
    public static void check(DocumentContent read, boolean floats, boolean descriptions) {
        check(read, floats, descriptions, floats);
    }

    /** As {@link #check(DocumentContent, boolean, boolean)}; {@code borders} is false for a format that lost them. */
    public static void check(DocumentContent read, boolean floats, boolean descriptions, boolean borders) {
        List<PictureAttributes.Placed> pictures = PictureAttributes.in(read.document().getDefaultRootElement());
        assertEquals(2, pictures.size(), "pictures: " + pictures);
        Picture red = pictures.get(0).picture();
        Picture blue = pictures.get(1).picture();
        assertEquals(Picture.Placement.IN_LINE, red.placement());
        assertEquals(30, red.width(), 0.5);
        assertEquals(15, red.height(), 0.5);
        assertEquals(40, red.pixelWidth());
        assertNotNull(red.image());
        assertEquals(Color.RED.getRGB(), red.image().getRGB(5, 5) | 0xFF000000);
        // A format that lost the border keeps the frame around it as the size: up to 1.5 pt more.
        assertEquals(72, blue.width(), borders ? 0.5 : 2);
        assertEquals(Color.BLUE.getRGB(), blue.image().getRGB(5, 5) | 0xFF000000);
        assertEquals(floats ? Picture.Placement.FLOAT_LEFT : Picture.Placement.IN_LINE, blue.placement());
        if (borders) assertEquals(Picture.Border.THIN, blue.border());
        if (descriptions) {
            assertEquals("A red bar", red.description());
            assertEquals("A blue square", blue.description());
        }
        try {
            String text = read.document().getText(0, read.document().getLength());
            assertTrue(text.contains("Before " + PictureAttributes.OBJECT + " after."), text);
            assertTrue(text.contains("Floating text beside"), text);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }
}
