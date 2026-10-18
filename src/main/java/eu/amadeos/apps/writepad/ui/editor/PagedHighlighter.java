package eu.amadeos.apps.writepad.ui.editor;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.JTextComponent;

/**
 * Swing's highlighter, repainting what it removes or moves by text range.
 *
 * <p>For a highlight that the views paint themselves (the selection), {@link DefaultHighlighter} remembers
 * the rectangle it was last painted in and repaints only that when the highlight goes away or changes. The
 * page view paints its text in points, moved to the centre of the window and scaled by the zoom, so that
 * rectangle is not where the highlight is on the screen, and parts of an old selection stayed visible until
 * the next full repaint (for example on resizing the window). Repainting the old range through the views,
 * which map it to screen pixels, clears all of it.
 */
final class PagedHighlighter extends DefaultHighlighter {
    private JTextComponent component;

    @Override
    public void install(JTextComponent c) {
        component = c;
        super.install(c);
    }

    @Override
    public void deinstall(JTextComponent c) {
        super.deinstall(c);
        component = null;
    }

    @Override
    public void removeHighlight(Object tag) {
        Highlight old = (Highlight) tag;
        int p0 = old.getStartOffset();
        int p1 = old.getEndOffset();
        super.removeHighlight(tag);
        damage(p0, p1);
    }

    @Override
    public void changeHighlight(Object tag, int p0, int p1) throws BadLocationException {
        Highlight old = (Highlight) tag;
        int old0 = old.getStartOffset();
        int old1 = old.getEndOffset();
        super.changeHighlight(tag, p0, p1);
        // The new range is repainted by Swing; repaint the part of the old one that is no longer in it.
        if (old0 < p0 || old1 > p1) damage(old0, old1);
    }

    @Override
    public void removeAllHighlights() {
        super.removeAllHighlights();
        if (component != null) component.repaint();
    }

    private void damage(int p0, int p1) {
        JTextComponent c = component;
        if (c == null || p0 == p1) return;
        Runnable repaint = () -> {
            int length = c.getDocument().getLength();
            int start = Math.min(Math.min(p0, p1), length);
            int end = Math.min(Math.max(p0, p1), length);
            if (start < end) c.getUI().damageRange(c, start, end);
        };
        if (SwingUtilities.isEventDispatchThread()) repaint.run();
        else SwingUtilities.invokeLater(repaint);
    }
}
