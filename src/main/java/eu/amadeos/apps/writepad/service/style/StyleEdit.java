package eu.amadeos.apps.writepad.service.style;

import javax.swing.text.AttributeSet;
import javax.swing.text.StyledDocument;
import javax.swing.undo.AbstractUndoableEdit;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** A change of style definitions, as one step that Undo takes back. */
public final class StyleEdit extends AbstractUndoableEdit {
    private final StyledDocument document;
    private final Map<ParagraphStyle, AttributeSet> before = new EnumMap<>(ParagraphStyle.class);
    private final Map<ParagraphStyle, AttributeSet> after = new EnumMap<>(ParagraphStyle.class);

    private StyleEdit(StyledDocument document) {
        this.document = Objects.requireNonNull(document, "document");
    }

    /**
     * Gives styles new definitions and returns the edit that undoes it; null if nothing changed.
     */
    public static StyleEdit define(StyledDocument document, Map<ParagraphStyle, ? extends AttributeSet> definitions) {
        StyleEdit edit = new StyleEdit(document);
        for (Map.Entry<ParagraphStyle, ? extends AttributeSet> d : definitions.entrySet()) {
            AttributeSet old = ParagraphStyles.definition(document, d.getKey());
            AttributeSet now = ParagraphStyles.complete(d.getValue());
            if (old.isEqual(now)) continue;
            edit.before.put(d.getKey(), old);
            edit.after.put(d.getKey(), now);
        }
        if (edit.after.isEmpty()) return null;
        edit.after.forEach((style, values) -> ParagraphStyles.define(document, style, values));
        return edit;
    }

    @Override
    public void undo() {
        super.undo();
        before.forEach((style, values) -> ParagraphStyles.define(document, style, values));
    }

    @Override
    public void redo() {
        super.redo();
        after.forEach((style, values) -> ParagraphStyles.define(document, style, values));
    }

    @Override
    public String getPresentationName() {
        return "styles";
    }
}
