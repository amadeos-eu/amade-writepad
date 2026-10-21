package eu.amadeos.apps.writepad.ui.icons;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.ToolIcon;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritepadGlyphTest {
    @Test
    void everyGlyphDrawsSomethingInTheButtonColour() {
        JButton button = new JButton();
        button.setForeground(Color.BLACK);
        for (WritepadGlyph glyph : WritepadGlyph.values()) {
            BufferedImage image = paint(new ToolIcon(glyph), button);
            int ink = count(image, rgb -> (rgb & 0xFFFFFF) < 0x404040);
            assertTrue(ink >= 10, glyph + " draws only " + ink + " dark pixels");
        }
    }

    @Test
    void disabledButtonsUseTheInactiveColour() {
        JButton button = new JButton();
        button.setForeground(Color.BLACK);
        button.setEnabled(false);
        int inactive = AmadeUI.theme().textInactive().getRGB() & 0xFFFFFF;
        BufferedImage image = paint(new ToolIcon(WritepadGlyph.ALIGN_JUSTIFY), button);
        assertEquals(0, count(image, rgb -> (rgb & 0xFFFFFF) == 0));
        assertTrue(count(image, rgb -> (rgb & 0xFFFFFF) == inactive) > 20);
    }

    @Test
    void textColourShowsTheSwatchUntilTheButtonIsDisabled() {
        JButton button = new JButton();
        ToolIcon icon = new ToolIcon(new TextColorGlyph(() -> new Color(0x00C000)), 16);
        assertTrue(count(paint(icon, button), rgb -> (rgb & 0xFFFFFF) == 0x00C000) > 10);
        button.setEnabled(false);
        assertEquals(0, count(paint(icon, button), rgb -> (rgb & 0xFFFFFF) == 0x00C000));
    }

    private static BufferedImage paint(ToolIcon icon, JButton button) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xC0C0C0));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        icon.paintIcon(button, g, 0, 0);
        g.dispose();
        return image;
    }

    private static int count(BufferedImage image, IntPredicate test) {
        int n = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (test.test(image.getRGB(x, y))) n++;
            }
        }
        return n;
    }
}
