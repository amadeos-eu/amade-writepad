package eu.amadeos.apps.writepad.ui.icons;

import eu.amadeos.commons.swing.ToolIcon;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.function.Supplier;

// --8<-- [start:text-color]
/**
 * An "A" over a bar in the colour the button applies next. Not part of {@link WritepadGlyph}, because an
 * enum constant cannot hold the colour: the supplier is asked on every paint, so the button follows the
 * last chosen colour. Disabled, the bar is grey like the rest of the icon.
 */
public record TextColorGlyph(Supplier<Color> swatch) implements ToolIcon.Glyph {
    public TextColorGlyph {
        Objects.requireNonNull(swatch, "swatch");
    }

    @Override
    public void paint(ToolIcon.Pen pen) {
        pen.letter("A", new Font(Font.SERIF, Font.BOLD, 12), 10.5);
        pen.fillAndDraw(new Rectangle2D.Double(2.5, 11.5, 11, 3), pen.enabled() ? swatch.get() : pen.ink());
    }
}
// --8<-- [end:text-color]
