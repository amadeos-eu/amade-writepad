package org.datazuul.amadeos.writepad.ui.editor;

import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.domain.Margins;
import org.datazuul.amadeos.writepad.domain.Orientation;
import org.datazuul.amadeos.writepad.domain.PaperSize;
import org.datazuul.amadeos.writepad.OnEdt;
import org.datazuul.amadeos.writepad.service.layout.PageLayout;
import org.junit.jupiter.api.Test;

import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Font;
import java.awt.Point;
import java.awt.font.FontRenderContext;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Page breaks, zoom and hit-testing of the page view, on a real {@link PageEditor}. */
class PageEditorTest {
    private PageLayout layout;
    private PageEditor editor;

    private void withSample(DocumentSettings settings, double zoom, Runnable test) throws Exception {
        onEdt(() -> {
            layout = new PageLayout(settings, zoom);
            editor = new PageEditor(layout);
            SampleText.appendTo(editor.getStyledDocument(), 6);
            fit();
            test.run();
        });
    }

    private void fit() {
        editor.setSize(editor.getPreferredSize());
        editor.layOut();
        editor.setSize(editor.getPreferredSize());
    }

    @Test
    void sampleFillsSeveralA4Pages() throws Exception {
        withSample(DocumentSettings.DEFAULT, 1, () -> {
            int pages = editor.pageCount();
            assertTrue(pages >= 4, "pages: " + pages);
            assertEquals(layout.totalHeight(pages), editor.getPreferredSize().height, 1);
            // Swing adds a pixel for the caret at the end of a line.
            assertEquals(layout.totalWidth(), editor.getPreferredSize().width, 1);
            assertLinesInsideTextArea();
        });
    }

    @Test
    void smallerPaperNeedsMorePages() throws Exception {
        withSample(DocumentSettings.DEFAULT, 1, () -> {
            int a4 = editor.pageCount();
            layout.setSettings(DocumentSettings.DEFAULT.withPaper(PaperSize.A5));
            fit();
            int a5 = editor.pageCount();
            assertTrue(a5 > a4, "A4 " + a4 + ", A5 " + a5);
            assertLinesInsideTextArea();
            layout.setSettings(DocumentSettings.DEFAULT.withPaper(PaperSize.A5).withOrientation(Orientation.LANDSCAPE));
            fit();
            // Swing adds a pixel for the caret at the end of a line.
            assertEquals(layout.totalWidth(), editor.getPreferredSize().width, 1);
            assertTrue(editor.pageCount() > a4);
            assertLinesInsideTextArea();
        });
    }

    @Test
    void widerMarginsReflowTheText() throws Exception {
        withSample(DocumentSettings.DEFAULT, 1, () -> {
            int before = editor.pageCount();
            layout.setSettings(DocumentSettings.DEFAULT.withMargins(Margins.ofMm(40, 50, 40, 50)));
            fit();
            assertTrue(editor.pageCount() > before);
            assertLinesInsideTextArea();
        });
    }

    @Test
    void zoomScalesTheViewAndKeepsTheLayout() throws Exception {
        withSample(DocumentSettings.DEFAULT, 1, () -> {
            int pages = editor.pageCount();
            Rectangle at100 = view(500);
            layout.setZoom(2);
            fit();
            assertEquals(pages, editor.pageCount());
            assertEquals(2 * layout.totalWidth(), editor.getPreferredSize().width, 1);
            Rectangle at200 = view(500);
            assertEquals(2 * at100.y, at200.y, 2);
            assertEquals(2 * at100.x, at200.x, 2);
            assertLinesInsideTextArea();
        });
    }

    @Test
    void clicksFindTheirTextAtEveryZoom() throws Exception {
        for (double zoom : new double[]{0.5, 1, 4.0 / 3, 2}) {
            withSample(DocumentSettings.DEFAULT, zoom, () -> {
                int length = editor.getDocument().getLength();
                for (int offset = 0; offset < length; offset += 41) {
                    Rectangle r = view(offset);
                    int hit = editor.viewToModel2D(new Point(r.x + 1, r.y + r.height / 2));
                    assertTrue(Math.abs(hit - offset) <= 1, "zoom " + zoom + ": " + offset + " -> " + hit);
                }
            });
        }
    }

    /**
     * The caret at the end of a line sits where the painted text ends: lines are measured with the same
     * fractional advances they are painted with, not with whole pixels per glyph.
     */
    @Test
    void lineEndCaretMatchesThePaintedText() throws Exception {
        String line = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";
        for (double zoom : new double[]{1, 4.0 / 3, 2}) {
            onEdt(() -> {
                layout = new PageLayout(DocumentSettings.DEFAULT, zoom);
                editor = new PageEditor(layout);
                try {
                    editor.getDocument().insertString(0, line, null);
                } catch (BadLocationException e) {
                    throw new AssertionError(e);
                }
                fit();
                double measured = (view(line.length()).x - view(0).x) / zoom;
                Font font = editor.getStyledDocument().getFont(editor.getStyledDocument().getCharacterElement(0)
                        .getAttributes());
                double painted = font.getStringBounds(line, new FontRenderContext(null, true, true)).getWidth();
                assertEquals(painted, measured, 0.75 + 1 / zoom, "zoom " + zoom);
            });
        }
    }

    /** A click right of a paragraph's last line puts the caret after its last character, where Enter splits. */
    @Test
    void clickAfterLineEndGoesToTheEndOfTheParagraph() throws Exception {
        for (double zoom : new double[]{0.5, 1, 4.0 / 3, 2}) {
            withSample(DocumentSettings.DEFAULT, zoom, () -> {
                int end = editor.getStyledDocument().getParagraphElement(200).getEndOffset() - 1;
                Rectangle last = view(end);
                int right = (int) ((PageLayout.GAP + layout.pageWidth() - layout.right()) * zoom) - 2;
                int hit = editor.viewToModel2D(new Point(right, last.y + last.height / 2));
                assertEquals(end, hit, "zoom " + zoom);
            });
        }
    }

    /** Tab stops measured from the margin, the same before and after painting, at every zoom. */
    @Test
    void tabsLandOnTheirStops() throws Exception {
        for (double zoom : new double[]{1, 4.0 / 3}) {
            OnEdt.run(() -> {
                layout = new PageLayout(DocumentSettings.DEFAULT, zoom);
                editor = new PageEditor(layout);
                StyledDocument doc = editor.getStyledDocument();
                try {
                    doc.insertString(0, "a\tL\tR\tC c\tx\n1.\tHanging\nC\tcentred", null);
                } catch (BadLocationException e) {
                    throw new AssertionError(e);
                }
                SimpleAttributeSet tabs = new SimpleAttributeSet();
                StyleConstants.setTabSet(tabs, new TabSet(new TabStop[]{new TabStop(100),
                        new TabStop(200, TabStop.ALIGN_RIGHT, TabStop.LEAD_NONE),
                        new TabStop(300, TabStop.ALIGN_CENTER, TabStop.LEAD_NONE)}));
                doc.setParagraphAttributes(0, 1, tabs, false);
                SimpleAttributeSet hanging = new SimpleAttributeSet();
                StyleConstants.setLeftIndent(hanging, 50);
                StyleConstants.setFirstLineIndent(hanging, -30);
                doc.setParagraphAttributes(text().indexOf("1."), 1, hanging, false);
                SimpleAttributeSet centred = new SimpleAttributeSet();
                StyleConstants.setAlignment(centred, StyleConstants.ALIGN_CENTER);
                doc.setParagraphAttributes(text().indexOf("C\tc"), 1, centred, false);
                // One pixel at this zoom, as modelToView answers in whole pixels.
                double px = 1 / zoom + 0.05;
                for (int round = 0; round < 2; round++) {
                    fit();
                    assertEquals(100, margin("L"), px, "left tab, zoom " + zoom);
                    assertEquals(200, margin("\tC") , px, "right tab ends at the stop");
                    float centre = (margin("C c") + margin("\tx")) / 2;
                    assertEquals(300, centre, px, "centre tab");
                    assertEquals(324, margin("x"), px, "default stop after the last");
                    assertEquals(50, margin("Hanging"), px, "hanging indent is a stop");
                    float gap = margin("centred") - margin("\tcentred");
                    assertEquals(18, gap, px, "fixed gap in centred paragraphs");
                    // Painting must not move anything.
                    BufferedImage image = new BufferedImage(editor.getWidth(), 200, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = image.createGraphics();
                    editor.paint(g);
                    g.dispose();
                }
            });
        }
    }

    private String text() {
        try {
            return editor.getDocument().getText(0, editor.getDocument().getLength());
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    /** Points from the left margin of the first occurrence of the text. */
    private float margin(String text) {
        int offset = text().indexOf(text);
        assertTrue(offset >= 0, text);
        return (float) ((view(offset).getX() - editor.textColumnX()) / layout.zoom());
    }

    /** A list item's text starts at the left indent on every line; the label is painted before it. */
    @Test
    void listItemsShowTheirLabelInFrontOfTheText() throws Exception {
        OnEdt.run(() -> {
            layout = new PageLayout(DocumentSettings.DEFAULT, 2);
            editor = new PageEditor(layout);
            try {
                editor.getDocument().insertString(0, "Item with enough text to wrap onto a second line at this width, "
                        + "and a few more words so that it surely does.", null);
            } catch (BadLocationException e) {
                throw new AssertionError(e);
            }
            new TextFormatting(editor).toggleNumbering();
            fit();
            assertEquals(18, margin("Item"), 0.6, "first line at the left indent, not at the label");
            int secondLine = text().indexOf("surely");
            assertEquals(18, margin(text().substring(firstOnSecondLine())), 0.6);
            BufferedImage image = new BufferedImage(editor.getWidth(), editor.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            editor.paint(g);
            g.dispose();
            Rectangle first = view(0);
            int ink = 0;
            for (int x = (int) editor.textColumnX(); x < first.x - 2; x++) {
                for (int y = first.y; y < first.y + first.height; y++) {
                    if ((image.getRGB(x, y) & 0xFF) < 100) ink++;
                }
            }
            assertTrue(ink > 20, "the label \"1.\" is painted in front of the text: " + ink + " dark pixels");
            assertTrue(secondLine > 0);
        });
    }

    /** The text of the second line, from its first character. */
    private int firstOnSecondLine() {
        int firstY = view(0).y;
        for (int offset = 1; offset < text().length(); offset++) {
            if (view(offset).y > firstY) return offset;
        }
        throw new AssertionError("no second line");
    }

    /** After a line break the text goes on on the next line, at the paragraph's (or list item's) indent. */
    @Test
    void lineBreaksStartANewLine() throws Exception {
        for (double zoom : new double[]{1, 4.0 / 3}) {
            OnEdt.run(() -> {
                layout = new PageLayout(DocumentSettings.DEFAULT, zoom);
                editor = new PageEditor(layout);
                try {
                    editor.getDocument().insertString(0, "First\u2028Second\u2028\u2028Fourth\nItem\u2028more", null);
                } catch (BadLocationException e) {
                    throw new AssertionError(e);
                }
                editor.setCaretPosition(text().indexOf("Item"));
                new TextFormatting(editor).toggleNumbering();
                fit();
                Rectangle first = view(0);
                Rectangle second = view(text().indexOf("Second"));
                Rectangle fourth = view(text().indexOf("Fourth"));
                assertEquals(first.x, second.x, 1, "same indent");
                assertTrue(second.y > first.y + first.height / 2, "next line");
                assertEquals(2 * (second.y - first.y), fourth.y - first.y, 2, "an empty line in between");
                assertEquals(0, editor.getStyledDocument().getDefaultRootElement().getElementIndex(
                        text().indexOf("Fourth")), "one paragraph");
                Rectangle item = view(text().indexOf("Item"));
                Rectangle more = view(text().indexOf("more"));
                assertEquals(item.x, more.x, 1, "list item's second line at the text indent");
                assertEquals(18, margin("more"), 1 / zoom + 0.1);
                assertTrue(more.y > item.y);
                // The caret can sit at the end of the line before the break.
                Rectangle end = view(text().indexOf("\u2028"));
                assertEquals(first.y, end.y, "zoom " + zoom);
                assertTrue(end.x > first.x);
            });
        }
    }

    /** A justified line that ends in a break is not stretched, as in word processors. */
    @Test
    void justifiedLineBeforeABreakIsNotStretched() throws Exception {
        OnEdt.run(() -> {
            layout = new PageLayout(DocumentSettings.DEFAULT, 1);
            editor = new PageEditor(layout);
            try {
                editor.getDocument().insertString(0, "Short words here\u2028next", null);
            } catch (BadLocationException e) {
                throw new AssertionError(e);
            }
            SimpleAttributeSet justified = new SimpleAttributeSet();
            StyleConstants.setAlignment(justified, StyleConstants.ALIGN_JUSTIFIED);
            editor.getStyledDocument().setParagraphAttributes(0, 1, justified, false);
            fit();
            float here = margin("here");
            Font font = editor.getStyledDocument().getFont(editor.getStyledDocument().getCharacterElement(0)
                    .getAttributes());
            double natural = font.getStringBounds("Short words ", new FontRenderContext(null, true, true)).getWidth();
            assertEquals(natural, here, 1.5);
        });
    }

    @Test
    void pagesAreCentredInAWiderWindow() throws Exception {
        withSample(DocumentSettings.DEFAULT, 1, () -> {
            int left = view(0).x;
            editor.setSize(editor.getPreferredSize().width + 200, editor.getPreferredSize().height);
            assertEquals(left + 100, view(0).x, 1);
        });
    }

    @Test
    void pageOfFollowsTheText() throws Exception {
        withSample(DocumentSettings.DEFAULT, 1, () -> {
            assertEquals(0, editor.pageOf(0));
            assertEquals(editor.pageCount() - 1, editor.pageOf(editor.getDocument().getLength()));
        });
    }

    /** Every line lies between the top and bottom margin of its page. */
    private void assertLinesInsideTextArea() {
        double zoom = layout.zoom();
        double tolerance = 2 / zoom + 0.5;
        int length = editor.getDocument().getLength();
        for (int offset = 0; offset < length; offset += 11) {
            Rectangle r = view(offset);
            double y = r.y / zoom;
            double bottom = y + r.height / zoom;
            int page = layout.pageAt(y);
            assertTrue(y >= layout.contentTop(page) - tolerance && bottom <= layout.contentBottom(page) + tolerance,
                    "offset " + offset + " at " + y + ".." + bottom + " outside page " + page);
        }
    }

    private Rectangle view(int offset) {
        try {
            return editor.modelToView2D(offset).getBounds();
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    static void onEdt(Runnable test) throws Exception {
        OnEdt.run(test);
    }
}
