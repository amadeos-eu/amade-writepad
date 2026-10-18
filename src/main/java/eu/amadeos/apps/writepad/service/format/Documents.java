package eu.amadeos.apps.writepad.service.format;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

/** Helpers shared by the formats. */
public final class Documents {
    /** Font of text without explicit formatting, also the editor's default. */
    public static final String DEFAULT_FAMILY = "Serif";
    public static final int DEFAULT_SIZE = 12;

    private Documents() {
    }

    /** An empty document whose base style uses the default font. */
    public static StyledDocument newDocument() {
        DefaultStyledDocument document = new WritepadDocument();
        Style base = document.getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setFontFamily(base, DEFAULT_FAMILY);
        StyleConstants.setFontSize(base, DEFAULT_SIZE);
        return document;
    }
}
