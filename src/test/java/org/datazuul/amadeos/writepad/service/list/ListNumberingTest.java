package org.datazuul.amadeos.writepad.service.list;

import org.datazuul.amadeos.writepad.service.format.Documents;
import org.junit.jupiter.api.Test;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListNumberingTest {
    @Test
    void formatsLabels() {
        assertEquals("3.", ListFormat.DECIMAL.label(3, 0));
        assertEquals("c.", ListFormat.LOWER_LETTER.label(3, 1));
        assertEquals("aa.", ListFormat.LOWER_LETTER.label(27, 1));
        assertEquals("Z.", ListFormat.UPPER_LETTER.label(26, 0));
        assertEquals("iv.", ListFormat.LOWER_ROMAN.label(4, 2));
        assertEquals("MCMXCIV.", ListFormat.UPPER_ROMAN.label(1994, 0));
        assertEquals("•", ListFormat.BULLET.label(1, 0));
        assertEquals("◦", ListFormat.BULLET.label(5, 1));
        assertEquals("▪", ListFormat.BULLET.label(1, 2));
        assertEquals("•", ListFormat.BULLET.label(1, 3));
    }

    @Test
    void mapsRtfStylesAndDefaults() {
        assertEquals(ListFormat.UPPER_ROMAN, ListFormat.ofRtf(1));
        assertEquals(ListFormat.BULLET, ListFormat.ofRtf(23));
        assertEquals(ListFormat.DECIMAL, ListFormat.ofRtf(47), "unknown styles count as decimal");
        assertEquals(List.of(ListFormat.DECIMAL, ListFormat.LOWER_LETTER, ListFormat.LOWER_ROMAN, ListFormat.DECIMAL),
                List.of(ListFormat.numberingFor(0), ListFormat.numberingFor(1), ListFormat.numberingFor(2),
                        ListFormat.numberingFor(3)));
    }

    @Test
    void numbersByListAndLevel() throws BadLocationException {
        StyledDocument doc = Documents.newDocument();
        // list, level, format; list 0 = plain paragraph
        int[][] items = {{1, 0}, {1, 0}, {1, 1}, {1, 1}, {1, 0}, {0, 0}, {1, 0}, {2, 0}, {1, 1}};
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < items.length; i++) text.append("p").append(i).append(i < items.length - 1 ? "\n" : "");
        doc.insertString(0, text.toString(), null);
        Element root = doc.getDefaultRootElement();
        for (int i = 0; i < items.length; i++) {
            SimpleAttributeSet a = new SimpleAttributeSet();
            ListFormat format = items[i][1] == 0 ? ListFormat.DECIMAL : ListFormat.LOWER_LETTER;
            ListAttributes.set(a, items[i][0], items[i][1], format);
            doc.setParagraphAttributes(root.getElement(i).getStartOffset(), 1, a, false);
        }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < root.getElementCount(); i++) labels.add(ListNumbering.label(doc, root.getElement(i)));
        assertEquals(java.util.Arrays.asList("1.", "2.", "a.", "b.", "3.", null, "4.", "1.", "a."), labels,
                "sublists start again under each item; a plain paragraph does not interrupt; lists count apart");

        // Changing the document renumbers.
        SimpleAttributeSet out = new SimpleAttributeSet();
        ListAttributes.set(out, 0, 0, ListFormat.BULLET);
        doc.setParagraphAttributes(root.getElement(1).getStartOffset(), 1, out, false);
        assertEquals("3.", ListNumbering.label(doc, root.getElement(6)));
        assertEquals(null, ListNumbering.label(doc, root.getElement(1)));
    }
}
