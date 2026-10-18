package eu.amadeos.apps.writepad.ui.icons;

import eu.amadeos.commons.swing.AmadeUI;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolIconTest {
    @Test
    void everyGlyphDrawsSomethingInTheButtonColour() {
        JButton button = new JButton();
        button.setForeground(Color.BLACK);
        for (ToolIcon.Glyph glyph : ToolIcon.Glyph.values()) {
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
        BufferedImage image = paint(new ToolIcon(ToolIcon.Glyph.ALIGN_JUSTIFY), button);
        assertEquals(0, count(image, rgb -> (rgb & 0xFFFFFF) == 0));
        assertTrue(count(image, rgb -> (rgb & 0xFFFFFF) == inactive) > 20);
    }

    @Test
    void scalesToTheRequestedSize() {
        ToolIcon icon = new ToolIcon(ToolIcon.Glyph.SAVE, 32, () -> Color.RED);
        assertEquals(32, icon.getIconWidth());
        assertEquals(32, icon.getIconHeight());
        BufferedImage image = paint(icon, new JButton());
        assertTrue(count(image, rgb -> true) == 32 * 32);
    }

    @Test
    void textColourShowsTheSwatch() {
        BufferedImage image = paint(new ToolIcon(ToolIcon.Glyph.TEXT_COLOR, 16, () -> new Color(0x00C000)),
                new JButton());
        assertTrue(count(image, rgb -> (rgb & 0xFFFFFF) == 0x00C000) > 10);
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

    private static int count(BufferedImage image, java.util.function.IntPredicate test) {
        int n = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (test.test(image.getRGB(x, y))) n++;
            }
        }
        return n;
    }
}
