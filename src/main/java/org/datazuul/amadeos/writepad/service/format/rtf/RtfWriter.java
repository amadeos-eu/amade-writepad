package org.datazuul.amadeos.writepad.service.format.rtf;

import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.domain.Margins;
import org.datazuul.amadeos.writepad.domain.Orientation;
import org.datazuul.amadeos.writepad.domain.Units;
import org.datazuul.amadeos.writepad.service.format.DocumentContent;
import org.datazuul.amadeos.writepad.service.list.ListAttributes;
import org.datazuul.amadeos.writepad.service.list.ListFormat;
import org.datazuul.amadeos.writepad.service.list.ListNumbering;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writes a styled document as RTF that WordPad, Word and LibreOffice read: Windows-1252 characters as
 * {@code \'hh}, all others as <code>&#92;u</code> with a {@code ?} fallback, one group per run of equally formatted
 * text and a {@code \pard} per paragraph. Paragraphs are separated by {@code \par}, so reading the file
 * gives the same paragraphs back.
 */
final class RtfWriter {
    private static final Charset ANSI = Charset.forName("windows-1252");

    private final StyledDocument document;
    private final DocumentSettings settings;
    private final Map<String, Integer> fonts = new LinkedHashMap<>();
    private final Map<Color, Integer> colors = new LinkedHashMap<>();
    /** Our list ids by order of appearance, numbered from 1 as {@code \ls}, with their levels' styles. */
    private final Map<Integer, Integer> listNumbers = new LinkedHashMap<>();
    private final Map<Integer, ListFormat[]> listLevels = new LinkedHashMap<>();
    private final StringBuilder body = new StringBuilder();
    private final CharsetEncoder ansi = ANSI.newEncoder();

    private RtfWriter(DocumentContent content) {
        this.document = content.document();
        this.settings = content.settings();
    }

    static byte[] write(DocumentContent content) {
        return new RtfWriter(content).build().getBytes(StandardCharsets.US_ASCII);
    }

    private String build() {
        Element root = document.getDefaultRootElement();
        for (int p = 0; p < root.getElementCount(); p++) {
            Element paragraph = root.getElement(p);
            paragraph(paragraph);
            if (p < root.getElementCount() - 1) body.append("\\par\n");
        }
        StringBuilder rtf = new StringBuilder();
        rtf.append("{\\rtf1\\ansi\\ansicpg1252\\deff0\\uc1\n");
        rtf.append("{\\fonttbl");
        if (fonts.isEmpty()) fonts.put("Serif", 0);
        for (Map.Entry<String, Integer> font : fonts.entrySet()) {
            rtf.append("{\\f").append(font.getValue()).append(familyClass(font.getKey())).append("\\fcharset0 ");
            text(rtf, font.getKey());
            rtf.append(";}");
        }
        rtf.append("}\n");
        if (!colors.isEmpty()) {
            rtf.append("{\\colortbl;");
            for (Color color : colors.keySet()) {
                rtf.append("\\red").append(color.getRed()).append("\\green").append(color.getGreen())
                        .append("\\blue").append(color.getBlue()).append(';');
            }
            rtf.append("}\n");
        }
        if (!listNumbers.isEmpty()) lists(rtf);
        rtf.append("{\\*\\generator amade-writepad;}\n");
        page(rtf);
        rtf.append(body).append("}\n");
        return rtf.toString();
    }

    private void page(StringBuilder rtf) {
        Margins m = settings.margins();
        rtf.append("\\paperw").append(Units.pointsToTwips(settings.pageWidth()))
                .append("\\paperh").append(Units.pointsToTwips(settings.pageHeight()))
                .append("\\margl").append(Units.pointsToTwips(m.left()))
                .append("\\margr").append(Units.pointsToTwips(m.right()))
                .append("\\margt").append(Units.pointsToTwips(m.top()))
                .append("\\margb").append(Units.pointsToTwips(m.bottom()));
        if (settings.orientation() == Orientation.LANDSCAPE) rtf.append("\\landscape");
        rtf.append('\n');
    }

    private void paragraph(Element paragraph) {
        AttributeSet a = paragraph.getAttributes();
        int list = 0;
        if (ListAttributes.isListItem(a)) {
            list = listNumber(a);
            // The label as text, for programs that do not know lists; those that do skip it.
            String label = ListNumbering.label(document, paragraph);
            if (label != null) {
                body.append("{\\listtext\\pard\\plain ");
                text(body, label);
                body.append("\\tab}");
            }
        }
        body.append("\\pard");
        switch (StyleConstants.getAlignment(a)) {
            case StyleConstants.ALIGN_CENTER -> body.append("\\qc");
            case StyleConstants.ALIGN_RIGHT -> body.append("\\qr");
            case StyleConstants.ALIGN_JUSTIFIED -> body.append("\\qj");
            default -> body.append("\\ql");
        }
        twips("\\li", StyleConstants.getLeftIndent(a));
        twips("\\ri", StyleConstants.getRightIndent(a));
        twips("\\fi", StyleConstants.getFirstLineIndent(a));
        twips("\\sb", StyleConstants.getSpaceAbove(a));
        twips("\\sa", StyleConstants.getSpaceBelow(a));
        float lineSpacing = StyleConstants.getLineSpacing(a);
        if (lineSpacing > 0) body.append("\\sl").append(Math.round((1 + lineSpacing) * 240)).append("\\slmult1");
        if (list != 0) body.append("\\ls").append(list).append("\\ilvl").append(ListAttributes.level(a));
        TabSet tabs = StyleConstants.getTabSet(a);
        if (tabs != null) {
            for (int i = 0; i < tabs.getTabCount(); i++) tab(tabs.getTab(i));
        }
        body.append(' ');
        for (int r = 0; r < paragraph.getElementCount(); r++) {
            Element run = paragraph.getElement(r);
            int start = run.getStartOffset();
            int end = Math.min(run.getEndOffset(), document.getLength());
            String text;
            try {
                text = document.getText(start, Math.max(0, end - start));
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
            if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
            if (text.isEmpty()) continue;
            run(run.getAttributes(), text);
        }
    }

    private void run(AttributeSet a, String text) {
        body.append("{\\f").append(font(StyleConstants.getFontFamily(a)))
                .append("\\fs").append(StyleConstants.getFontSize(a) * 2);
        if (StyleConstants.isBold(a)) body.append("\\b");
        if (StyleConstants.isItalic(a)) body.append("\\i");
        if (StyleConstants.isUnderline(a)) body.append("\\ul");
        if (StyleConstants.isStrikeThrough(a)) body.append("\\strike");
        if (StyleConstants.isSuperscript(a)) body.append("\\super");
        if (StyleConstants.isSubscript(a)) body.append("\\sub");
        if (a.isDefined(StyleConstants.Foreground)) {
            body.append("\\cf").append(color(StyleConstants.getForeground(a)));
        }
        body.append(' ');
        text(body, text);
        body.append('}');
    }

    /**
     * The RTF font family, so that a program without the font (Word does not know Java's "Serif") picks a
     * similar one.
     */
    static String familyClass(String family) {
        return switch (family.toLowerCase(java.util.Locale.ROOT)) {
            case "serif", "times new roman", "liberation serif", "dejavu serif", "georgia" -> "\\froman";
            case "sansserif", "sans-serif", "dialog", "arial", "liberation sans", "dejavu sans", "helvetica",
                 "calibri", "verdana" -> "\\fswiss";
            case "monospaced", "dialoginput", "courier new", "liberation mono", "dejavu sans mono", "courier" ->
                    "\\fmodern";
            default -> "\\fnil";
        };
    }

    /** The {@code \ls} number of the paragraph's list; remembers the style of each level as first used. */
    private int listNumber(AttributeSet a) {
        int id = ListAttributes.list(a);
        int number = listNumbers.computeIfAbsent(id, k -> listNumbers.size() + 1);
        ListFormat[] levels = listLevels.computeIfAbsent(id, k -> new ListFormat[ListAttributes.MAX_LEVEL + 1]);
        int level = ListAttributes.level(a);
        if (levels[level] == null) levels[level] = ListAttributes.format(a);
        return number;
    }

    /**
     * The list table and the override table: one list per list of the document, its levels in the styles
     * used (levels not used follow the first one: all bullets, or 1. a. i. for numbering).
     */
    private void lists(StringBuilder rtf) {
        rtf.append("{\\*\\listtable");
        for (Map.Entry<Integer, Integer> list : listNumbers.entrySet()) {
            int number = list.getValue();
            ListFormat[] levels = listLevels.get(list.getKey());
            ListFormat first = Arrays.stream(levels).filter(Objects::nonNull).findFirst().orElse(ListFormat.BULLET);
            rtf.append("\n{\\list\\listtemplateid").append(number).append("\\listsimple0");
            for (int level = 0; level < levels.length; level++) {
                ListFormat format = levels[level] != null ? levels[level]
                        : first.isBullet() ? ListFormat.BULLET : ListFormat.numberingFor(level);
                rtf.append("{\\listlevel\\levelnfc").append(format.rtfCode()).append("\\levelnfcn")
                        .append(format.rtfCode()).append("\\leveljc0\\leveljcn0\\levelfollow0\\levelstartat1");
                if (format.isBullet()) {
                    String bullet = format.label(1, level);
                    rtf.append("{\\leveltext\\'01");
                    text(rtf, bullet);
                    rtf.append(";}{\\levelnumbers;}");
                } else {
                    rtf.append("{\\leveltext\\'02\\'").append(String.format("%02x", level)).append(".;}")
                            .append("{\\levelnumbers\\'01;}");
                }
                int indent = Units.pointsToTwips(ListAttributes.leftIndent(level));
                rtf.append("\\fi-").append(Units.pointsToTwips(ListAttributes.STEP)).append("\\li").append(indent)
                        .append("\\lin").append(indent).append('}');
            }
            rtf.append("{\\listname ;}\\listid").append(number).append('}');
        }
        rtf.append("}\n{\\*\\listoverridetable");
        for (int number : listNumbers.values()) {
            rtf.append("{\\listoverride\\listid").append(number).append("\\listoverridecount0\\ls").append(number)
                    .append('}');
        }
        rtf.append("}\n");
    }

    private void tab(TabStop tab) {
        switch (tab.getAlignment()) {
            case TabStop.ALIGN_CENTER -> body.append("\\tqc");
            case TabStop.ALIGN_RIGHT -> body.append("\\tqr");
            case TabStop.ALIGN_DECIMAL -> body.append("\\tqdec");
            default -> {
            }
        }
        switch (tab.getLeader()) {
            case TabStop.LEAD_DOTS -> body.append("\\tldot");
            case TabStop.LEAD_HYPHENS -> body.append("\\tlhyph");
            case TabStop.LEAD_UNDERLINE -> body.append("\\tlul");
            case TabStop.LEAD_THICKLINE -> body.append("\\tlth");
            case TabStop.LEAD_EQUALS -> body.append("\\tleq");
            default -> {
            }
        }
        body.append("\\tx").append(Math.max(0, Units.pointsToTwips(tab.getPosition())));
    }

    private void twips(String word, float points) {
        int twips = Units.pointsToTwips(points);
        if (twips != 0) body.append(word).append(twips);
    }

    private int font(String family) {
        return fonts.computeIfAbsent(family, f -> fonts.size());
    }

    private int color(Color color) {
        Color opaque = new Color(color.getRed(), color.getGreen(), color.getBlue());
        return colors.computeIfAbsent(opaque, c -> colors.size() + 1);
    }

    /** Escapes RTF's special characters; non-ASCII as code page bytes or Unicode. */
    private void text(StringBuilder out, String text) {
        List<Integer> codePoints = new ArrayList<>();
        text.codePoints().forEach(codePoints::add);
        for (int cp : codePoints) {
            switch (cp) {
                case '\\' -> out.append("\\\\");
                case '{' -> out.append("\\{");
                case '}' -> out.append("\\}");
                case '\t' -> out.append("\\tab ");
                case '\n' -> out.append("\\par\n");
                case '\u00A0' -> out.append("\\~");
                default -> {
                    if (cp >= 0x20 && cp < 0x7F) {
                        out.append((char) cp);
                    } else if (cp < 0x20) {
                        // other control characters have no place in text
                    } else {
                        nonAscii(out, cp);
                    }
                }
            }
        }
    }

    private void nonAscii(StringBuilder out, int cp) {
        String s = new String(Character.toChars(cp));
        if (ansi.canEncode(s)) {
            try {
                ByteBuffer encoded = ansi.encode(CharBuffer.wrap(s));
                while (encoded.hasRemaining()) {
                    out.append("\\'").append(String.format("%02x", encoded.get() & 0xFF));
                }
                return;
            } catch (CharacterCodingException e) {
                // fall through to Unicode
            }
        }
        for (char unit : Character.toChars(cp)) {
            out.append("\\u").append((short) unit).append('?');
        }
    }
}
