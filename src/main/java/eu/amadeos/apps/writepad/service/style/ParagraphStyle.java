package eu.amadeos.apps.writepad.service.style;

import eu.amadeos.apps.writepad.service.format.Documents;

import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.Color;
import java.util.Locale;
import java.util.Optional;

/**
 * The built-in paragraph styles: Normal, Title, Subtitle, Heading 1 to 6, Quote and Code. Each has a look of
 * its own (its built-in definition), an outline level for the headings, and the names the file formats know
 * it by, so that LibreOffice and Word see their own Heading 1 in a file WritePad saved, and the other way
 * round.
 */
public enum ParagraphStyle {
    // --8<-- [start:styles]
    NORMAL("Normal", 0, "Standard", null, "Normal", "Normal", "Normal"),
    TITLE("Title", 0, "Title", null, "Title", "Title", "Title"),
    SUBTITLE("Subtitle", 0, "Subtitle", null, "Subtitle", "Subtitle", "Subtitle"),
    HEADING_1("Heading 1", 1, "Heading_20_1", "Heading 1", "Heading1", "heading 1", "heading 1"),
    HEADING_2("Heading 2", 2, "Heading_20_2", "Heading 2", "Heading2", "heading 2", "heading 2"),
    HEADING_3("Heading 3", 3, "Heading_20_3", "Heading 3", "Heading3", "heading 3", "heading 3"),
    HEADING_4("Heading 4", 4, "Heading_20_4", "Heading 4", "Heading4", "heading 4", "heading 4"),
    HEADING_5("Heading 5", 5, "Heading_20_5", "Heading 5", "Heading5", "heading 5", "heading 5"),
    HEADING_6("Heading 6", 6, "Heading_20_6", "Heading 6", "Heading6", "heading 6", "heading 6"),
    QUOTE("Quote", 0, "Quotations", null, "Quote", "Quote", "Quote"),
    CODE("Code", 0, "Preformatted_20_Text", "Preformatted Text", "HTMLPreformatted", "HTML Preformatted",
            "HTML Preformatted");
    // --8<-- [end:styles]

    /** Dark blue of titles and headings. */
    private static final Color BLUE = new Color(0x1F, 0x4E, 0x79);
    private static final Color GREY = new Color(0x59, 0x59, 0x59);
    private static final Color DARK = new Color(0x40, 0x40, 0x40);
    private static final String SANS = "SansSerif";

    private final String swingName;
    private final int outlineLevel;
    private final String odtName;
    private final String odtDisplayName;
    private final String docxId;
    private final String docxName;
    private final String rtfName;

    ParagraphStyle(String swingName, int outlineLevel, String odtName, String odtDisplayName, String docxId,
                   String docxName, String rtfName) {
        this.swingName = swingName;
        this.outlineLevel = outlineLevel;
        this.odtName = odtName;
        this.odtDisplayName = odtDisplayName;
        this.docxId = docxId;
        this.docxName = docxName;
        this.rtfName = rtfName;
    }

    /** The name of the style in the Swing document; never shown (the menus translate it). */
    public String swingName() {
        return swingName;
    }

    /** 1 to 6 for the headings, 0 for the other styles. */
    public int outlineLevel() {
        return outlineLevel;
    }

    public boolean isHeading() {
        return outlineLevel > 0;
    }

    /** The key of the style's name in the messages: {@code style.heading-1}. */
    public String messageKey() {
        return "style." + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public String odtName() {
        return odtName;
    }

    /** The {@code style:display-name}, or null where it is the same as the name. */
    public String odtDisplayName() {
        return odtDisplayName;
    }

    public String docxId() {
        return docxId;
    }

    public String docxName() {
        return docxName;
    }

    public String rtfName() {
        return rtfName;
    }

    /** The style a new paragraph gets after Enter at the end of one in this style: Normal after headings. */
    public ParagraphStyle next() {
        return this == NORMAL || this == QUOTE || this == CODE ? this : NORMAL;
    }

    /** The heading of a level from 1 to 6, empty for any other level. */
    public static Optional<ParagraphStyle> heading(int level) {
        for (ParagraphStyle s : values()) {
            if (s.outlineLevel == level && level > 0) return Optional.of(s);
        }
        return Optional.empty();
    }

    /**
     * The built-in style a file's style name stands for, whatever program wrote it: LibreOffice's
     * {@code Heading_20_1} or {@code Heading 1}, Word's {@code heading 1}, {@code Quotations} and {@code Quote}.
     * Body text styles ({@code Text body}, {@code Body Text}) count as Normal; empty for other names.
     */
    public static Optional<ParagraphStyle> forName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String n = name.replace("_20_", " ").toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
        return Optional.ofNullable(switch (n) {
            case "normal", "standard", "default", "defaultparagraphstyle", "defaultstyle", "textbody", "bodytext" ->
                    NORMAL;
            case "title" -> TITLE;
            case "subtitle" -> SUBTITLE;
            case "heading1" -> HEADING_1;
            case "heading2" -> HEADING_2;
            case "heading3" -> HEADING_3;
            case "heading4" -> HEADING_4;
            case "heading5" -> HEADING_5;
            case "heading6" -> HEADING_6;
            case "quote", "quotation", "quotations", "blockquotation", "blocktext" -> QUOTE;
            case "code", "preformattedtext", "htmlpreformatted", "sourcecode" -> CODE;
            default -> null;
        });
    }

    // --8<-- [start:built-in]
    /** The style's look in a new document: every key of {@link ParagraphStyles#KEYS} set, but black text. */
    public AttributeSet builtIn() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        String family = Documents.DEFAULT_FAMILY;
        int size = Documents.DEFAULT_SIZE;
        boolean bold = false;
        boolean italic = false;
        Color color = Color.BLACK;
        float before = 0;
        float after = 0;
        float indent = 0;
        switch (this) {
            case NORMAL -> {
            }
            case TITLE -> {
                family = SANS;
                size = 26;
                bold = true;
                color = BLUE;
                after = 6;
            }
            case SUBTITLE -> {
                family = SANS;
                size = 15;
                color = GREY;
                after = 12;
            }
            case HEADING_1 -> {
                family = SANS;
                size = 18;
                bold = true;
                color = BLUE;
                before = 18;
                after = 6;
            }
            case HEADING_2 -> {
                family = SANS;
                size = 15;
                bold = true;
                color = BLUE;
                before = 14;
                after = 4;
            }
            case HEADING_3 -> {
                family = SANS;
                size = 13;
                bold = true;
                color = BLUE;
                before = 12;
                after = 4;
            }
            case HEADING_4 -> {
                family = SANS;
                bold = true;
                italic = true;
                color = BLUE;
                before = 10;
                after = 2;
            }
            case HEADING_5 -> {
                family = SANS;
                size = 11;
                bold = true;
                color = BLUE;
                before = 10;
                after = 2;
            }
            case HEADING_6 -> {
                family = SANS;
                size = 11;
                bold = true;
                italic = true;
                color = DARK;
                before = 8;
                after = 2;
            }
            case QUOTE -> {
                italic = true;
                color = DARK;
                before = 6;
                after = 6;
                indent = 28.35f; // 1 cm
            }
            case CODE -> {
                family = "Monospaced";
                size = 10;
            }
        }
        StyleConstants.setFontFamily(a, family);
        StyleConstants.setFontSize(a, size);
        StyleConstants.setBold(a, bold);
        StyleConstants.setItalic(a, italic);
        StyleConstants.setUnderline(a, false);
        StyleConstants.setStrikeThrough(a, false);
        StyleConstants.setSuperscript(a, false);
        StyleConstants.setSubscript(a, false);
        if (!color.equals(Color.BLACK)) StyleConstants.setForeground(a, color);
        StyleConstants.setAlignment(a, StyleConstants.ALIGN_LEFT);
        StyleConstants.setLeftIndent(a, indent);
        StyleConstants.setRightIndent(a, indent);
        StyleConstants.setFirstLineIndent(a, 0);
        StyleConstants.setSpaceAbove(a, before);
        StyleConstants.setSpaceBelow(a, after);
        StyleConstants.setLineSpacing(a, 0);
        return a;
    }
    // --8<-- [end:built-in]
}
