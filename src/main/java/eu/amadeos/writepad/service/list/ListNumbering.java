package eu.amadeos.writepad.service.list;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The labels of list items: "•", "3.", "b.", .... Numbers count the items of a list at their level, in
 * document order; an item at a higher level (less indented) starts the deeper levels again from 1.
 * Paragraphs that are not in the list do not interrupt it.
 *
 * <p>The labels of a document are computed once and kept until the document changes.
 */
public final class ListNumbering {
    private static final Map<Document, Labels> CACHE = new WeakHashMap<>();

    private ListNumbering() {
    }

    /** The label of a paragraph, null if it is not a list item. */
    public static synchronized String label(StyledDocument document, Element paragraph) {
        if (!ListAttributes.isListItem(paragraph.getAttributes())) return null;
        Labels labels = CACHE.get(document);
        if (labels == null) {
            labels = new Labels(document);
            CACHE.put(document, labels);
            document.addDocumentListener(labels);
        }
        return labels.of(paragraph);
    }

    /** Labels of all items, recomputed after any change of the document. */
    private static final class Labels implements DocumentListener {
        private final StyledDocument document;
        private Map<Element, String> labels;

        Labels(StyledDocument document) {
            this.document = document;
        }

        String of(Element paragraph) {
            if (labels == null) labels = compute(document);
            return labels.get(paragraph);
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            clear();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            clear();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            clear();
        }

        private void clear() {
            synchronized (ListNumbering.class) {
                labels = null;
            }
        }
    }

    static Map<Element, String> compute(StyledDocument document) {
        Map<Element, String> labels = new IdentityHashMap<>();
        Map<Integer, int[]> counters = new HashMap<>();
        Element root = document.getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++) {
            Element paragraph = root.getElement(i);
            AttributeSet a = paragraph.getAttributes();
            int list = ListAttributes.list(a);
            if (list == 0) continue;
            int level = ListAttributes.level(a);
            int[] count = counters.computeIfAbsent(list, id -> new int[ListAttributes.MAX_LEVEL + 1]);
            count[level]++;
            for (int deeper = level + 1; deeper < count.length; deeper++) count[deeper] = 0;
            labels.put(paragraph, ListAttributes.format(a).label(count[level], level));
        }
        return labels;
    }
}
