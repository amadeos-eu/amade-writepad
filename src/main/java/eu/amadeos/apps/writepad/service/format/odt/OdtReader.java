package eu.amadeos.apps.writepad.service.format.odt;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.Margins;
import eu.amadeos.apps.writepad.domain.Orientation;
import eu.amadeos.apps.writepad.domain.PaperSize;
import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.list.ListAttributes;
import eu.amadeos.apps.writepad.service.list.ListFormat;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads an OpenDocument text file into a styled document. Styles are resolved with their inheritance, lists
 * become list attributes (nested lists give the level, {@code text:continue-list} and
 * {@code text:continue-numbering} join lists), and the page layout of the first master page becomes the page
 * setup. Of what the model doesn't know, tables and sections give their paragraphs, fields and links their
 * text; pictures, frames, notes and comments are skipped.
 */
final class OdtReader {
    private static final XMLInputFactory XML = XMLInputFactory.newFactory();

    static {
        XML.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        XML.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XML.setProperty(XMLInputFactory.IS_COALESCING, true);
    }

    /** Elements skipped with all they contain, wherever they are. */
    private static final Set<String> SKIPPED = Set.of(
            "text:tracked-changes", "text:sequence-decls", "text:variable-decls", "text:user-field-decls",
            "text:dde-connection-decls", "office:forms", "text:note", "office:annotation", "text:ruby-text",
            "text:table-of-content-source", "text:alphabetical-index-source", "text:illustration-index-source",
            "text:object-index-source", "text:user-index-source", "text:table-index-source",
            "text:bibliography-source", "table:table-columns", "table:table-column", "office:event-listeners");

    private final OdtStyles styles = new OdtStyles();
    private final StyledDocument document = Documents.newDocument();
    /** Our list ids for the lists' xml:id, and the last list of each list style. */
    private final Map<String, Integer> listsByXmlId = new HashMap<>();
    private final Map<String, Integer> lastListByStyle = new HashMap<>();
    private int lastList;
    private int nextListId = 1;

    /** Text waiting to be inserted, all in the same format. */
    private final StringBuilder text = new StringBuilder();
    private AttributeSet textFormat;
    /** ODF collapses white space: true after a space and at the start of a paragraph. */
    private boolean afterSpace;

    /** Where a paragraph sits in a list. */
    private record ListPlace(int list, int level, Map<Integer, OdtStyles.ListLevel> style, boolean labelled) {
    }

    private OdtReader() {
    }

    static DocumentContent read(byte[] bytes) throws IOException {
        Map<String, byte[]> files = unzip(bytes);
        byte[] content = files.get("content.xml");
        if (content == null) throw new IOException("Not an OpenDocument file: no content.xml");
        OdtReader reader = new OdtReader();
        try {
            if (files.containsKey("settings.xml")) reader.parseSettings(files.get("settings.xml"));
            if (files.containsKey("styles.xml")) reader.parse(files.get("styles.xml"));
            reader.parse(content);
        } catch (XMLStreamException e) {
            throw new IOException("Broken OpenDocument file: " + e.getMessage(), e);
        }
        reader.removeTrailingParagraphEnd();
        return new DocumentContent(reader.document, reader.settings());
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("content.xml") || name.equals("styles.xml") || name.equals("settings.xml")) {
                    files.put(name, zip.readAllBytes());
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Broken ZIP file", e);
        }
        return files;
    }

    // ---------------------------------------------------------------- XML helpers

    private static String name(XMLStreamReader r) {
        String prefix = Odf.prefix(r.getNamespaceURI());
        return (prefix != null ? prefix : "?") + ":" + r.getLocalName();
    }

    private static String attribute(XMLStreamReader r, String namespace, String name) {
        return r.getAttributeValue(namespace, name);
    }

    /** The attributes in the known namespaces, keyed as {@code fo:margin-left}. */
    private static Map<String, String> attributes(XMLStreamReader r) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < r.getAttributeCount(); i++) {
            String prefix = Odf.prefix(r.getAttributeNamespace(i));
            if (prefix != null) map.put(prefix + ":" + r.getAttributeLocalName(i), r.getAttributeValue(i));
        }
        return map;
    }

    /** Skips the element the reader is at, with everything in it. */
    private static void skip(XMLStreamReader r) throws XMLStreamException {
        int depth = 1;
        while (depth > 0 && r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) depth++;
            if (event == XMLStreamConstants.END_ELEMENT) depth--;
        }
    }

    /** Calls the handler for each child element of the element the reader is at, until its end. */
    private interface Children {
        void child(String name) throws XMLStreamException;
    }

    private static void children(XMLStreamReader r, Children handler) throws XMLStreamException {
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) handler.child(name(r));
            else if (event == XMLStreamConstants.END_ELEMENT) return;
        }
    }

    // ---------------------------------------------------------------- settings and styles

    private void parseSettings(byte[] xml) throws XMLStreamException {
        XMLStreamReader r = XML.createXMLStreamReader(new ByteArrayInputStream(xml));
        while (r.hasNext()) {
            if (r.next() == XMLStreamConstants.START_ELEMENT && name(r).equals("config:config-item")
                    && "TabsRelativeToIndent".equals(attribute(r, Odf.CONFIG, "name"))) {
                styles.tabsRelativeToIndent = !"false".equals(r.getElementText().strip());
            }
        }
    }

    /** Parses styles.xml or content.xml: fonts, styles, page layouts, and the text of the body. */
    private void parse(byte[] xml) throws XMLStreamException {
        XMLStreamReader r = XML.createXMLStreamReader(new ByteArrayInputStream(xml));
        while (r.hasNext()) {
            if (r.next() != XMLStreamConstants.START_ELEMENT) continue;
            switch (name(r)) {
                case "office:font-face-decls" -> children(r, child -> {
                    if (child.equals("style:font-face")) {
                        String family = attribute(r, Odf.SVG, "font-family");
                        String face = attribute(r, Odf.STYLE, "name");
                        if (face != null) styles.fontFaces.put(face, family != null ? unquote(family) : face);
                    }
                    skip(r);
                });
                case "office:styles" -> styleList(r, false);
                case "office:automatic-styles" -> styleList(r, true);
                case "office:master-styles" -> children(r, child -> {
                    if (child.equals("style:master-page")) {
                        String master = attribute(r, Odf.STYLE, "name");
                        styles.masterPages.put(master, attribute(r, Odf.STYLE, "page-layout-name"));
                        if (styles.firstMasterPage == null) styles.firstMasterPage = master;
                    }
                    skip(r);
                });
                case "office:text" -> {
                    block(r, null);
                    flushText();
                }
                default -> {
                }
            }
        }
    }

    private void styleList(XMLStreamReader r, boolean isAutomatic) throws XMLStreamException {
        children(r, child -> {
            switch (child) {
                case "style:default-style" -> styles.addDefault(attribute(r, Odf.STYLE, "family"), style(r, null));
                case "style:style" -> {
                    String family = attribute(r, Odf.STYLE, "family");
                    String name = attribute(r, Odf.STYLE, "name");
                    OdtStyles.Style style = style(r, name);
                    if (family != null && name != null) styles.add(family, style, isAutomatic);
                }
                case "text:list-style" -> {
                    String name = attribute(r, Odf.STYLE, "name");
                    Map<Integer, OdtStyles.ListLevel> levels = listStyle(r);
                    if (name != null) styles.addList(name, levels, isAutomatic);
                }
                case "style:page-layout" -> {
                    String name = attribute(r, Odf.STYLE, "name");
                    children(r, props -> {
                        if (props.equals("style:page-layout-properties")) {
                            styles.pageLayouts.put(name, attributes(r));
                        }
                        skip(r);
                    });
                }
                default -> skip(r);
            }
        });
    }

    private OdtStyles.Style style(XMLStreamReader r, String name) throws XMLStreamException {
        OdtStyles.Style style = new OdtStyles.Style(name, attribute(r, Odf.STYLE, "parent-style-name"));
        style.listStyle = attribute(r, Odf.STYLE, "list-style-name");
        children(r, child -> {
            switch (child) {
                case "style:paragraph-properties" -> {
                    style.paragraph.putAll(attributes(r));
                    children(r, inner -> {
                        if (inner.equals("style:tab-stops")) {
                            List<OdtStyles.Tab> tabs = new ArrayList<>();
                            children(r, tab -> {
                                if (tab.equals("style:tab-stop")) tabs.add(tab(attributes(r)));
                                skip(r);
                            });
                            style.tabs = tabs;
                        } else {
                            skip(r);
                        }
                    });
                }
                case "style:text-properties" -> {
                    style.text.putAll(attributes(r));
                    skip(r);
                }
                default -> skip(r);
            }
        });
        return style;
    }

    private static OdtStyles.Tab tab(Map<String, String> a) {
        double position = Odf.points(a.get("style:position"));
        int alignment = switch (a.getOrDefault("style:type", "left")) {
            case "center" -> TabStop.ALIGN_CENTER;
            case "right" -> TabStop.ALIGN_RIGHT;
            case "char" -> TabStop.ALIGN_DECIMAL;
            default -> TabStop.ALIGN_LEFT;
        };
        String leaderText = a.getOrDefault("style:leader-text", "");
        String leaderStyle = a.getOrDefault("style:leader-style", "none");
        int leader = switch (leaderText) {
            case "." -> TabStop.LEAD_DOTS;
            case "-" -> TabStop.LEAD_HYPHENS;
            case "_" -> TabStop.LEAD_UNDERLINE;
            case "=" -> TabStop.LEAD_EQUALS;
            default -> switch (leaderStyle) {
                case "dotted" -> TabStop.LEAD_DOTS;
                case "dash", "long-dash", "dot-dash", "dot-dot-dash" -> TabStop.LEAD_HYPHENS;
                case "solid", "wave" -> TabStop.LEAD_UNDERLINE;
                default -> TabStop.LEAD_NONE;
            };
        };
        return new OdtStyles.Tab(Double.isNaN(position) ? 0 : position, alignment, leader);
    }

    private Map<Integer, OdtStyles.ListLevel> listStyle(XMLStreamReader r) throws XMLStreamException {
        Map<Integer, OdtStyles.ListLevel> levels = new HashMap<>();
        children(r, child -> {
            boolean number = child.equals("text:list-level-style-number");
            if (!number && !child.equals("text:list-level-style-bullet")
                    && !child.equals("text:list-level-style-image")) {
                skip(r);
                return;
            }
            int level;
            try {
                level = Integer.parseInt(attribute(r, Odf.TEXT, "level")) - 1;
            } catch (NumberFormatException e) {
                skip(r);
                return;
            }
            String numFormat = number ? attribute(r, Odf.STYLE, "num-format") : null;
            // A numbered level without a number format shows no label.
            ListFormat format = !number ? ListFormat.BULLET
                    : numFormat == null || numFormat.isEmpty() ? null : ListFormat.ofOdf(numFormat);
            double[] indent = {Double.NaN, Double.NaN};
            children(r, props -> {
                if (props.equals("style:list-level-properties")) {
                    double spaceBefore = Odf.points(attribute(r, Odf.TEXT, "space-before"));
                    double minLabel = Odf.points(attribute(r, Odf.TEXT, "min-label-width"));
                    if (!Double.isNaN(minLabel)) {
                        indent[0] = (Double.isNaN(spaceBefore) ? 0 : spaceBefore) + minLabel;
                        indent[1] = -minLabel;
                    }
                    children(r, alignment -> {
                        if (alignment.equals("style:list-level-label-alignment")) {
                            double left = Odf.points(attribute(r, Odf.FO, "margin-left"));
                            double first = Odf.points(attribute(r, Odf.FO, "text-indent"));
                            if (!Double.isNaN(left)) {
                                indent[0] = left;
                                indent[1] = Double.isNaN(first) ? 0 : first;
                            }
                        }
                        skip(r);
                    });
                } else {
                    skip(r);
                }
            });
            if (level >= 0) levels.put(level, new OdtStyles.ListLevel(format, indent[0], indent[1]));
        });
        return levels;
    }

    private static String unquote(String family) {
        String s = family.strip();
        int comma = s.indexOf(',');
        if (comma > 0 && !s.startsWith("'") && !s.startsWith("\"")) s = s.substring(0, comma).strip();
        if (s.length() >= 2 && (s.startsWith("'") && s.endsWith("'") || s.startsWith("\"") && s.endsWith("\""))) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    // ---------------------------------------------------------------- body

    /** The block content of the element the reader is at: paragraphs, lists, and containers of them. */
    private void block(XMLStreamReader r, ListPlace place) throws XMLStreamException {
        children(r, child -> blockChild(r, child, place));
    }

    private void blockChild(XMLStreamReader r, String child, ListPlace place) throws XMLStreamException {
        if (child.equals("text:p") || child.equals("text:h")) {
            paragraph(r, place);
        } else if (child.equals("text:list")) {
            list(r, place);
        } else if (SKIPPED.contains(child) || child.startsWith("draw:") || child.startsWith("?:")) {
            skip(r);
        } else {
            // Tables, sections, indexes: their paragraphs are kept, as the model has no tables yet.
            block(r, null);
        }
    }

    private void list(XMLStreamReader r, ListPlace outer) throws XMLStreamException {
        ListPlace place;
        if (outer == null) {
            String styleName = attribute(r, Odf.TEXT, "style-name");
            String continueList = attribute(r, Odf.TEXT, "continue-list");
            boolean continueNumbering = "true".equals(attribute(r, Odf.TEXT, "continue-numbering"));
            int id;
            if (continueList != null && listsByXmlId.containsKey(continueList)) {
                id = listsByXmlId.get(continueList);
            } else if (continueNumbering && (lastListByStyle.containsKey(styleName) || lastList != 0)) {
                id = lastListByStyle.getOrDefault(styleName, lastList);
            } else {
                id = nextListId++;
            }
            String xmlId = attribute(r, Odf.XML, "id");
            if (xmlId != null) listsByXmlId.put(xmlId, id);
            if (styleName != null) lastListByStyle.put(styleName, id);
            lastList = id;
            place = new ListPlace(id, 0, styles.list(styleName), true);
        } else {
            place = new ListPlace(outer.list(), outer.level() + 1, outer.style(), true);
        }
        children(r, child -> {
            if (child.equals("text:list-item") || child.equals("text:list-header")) {
                // Only the first paragraph of an item has a label; a header has none.
                boolean[] labelled = {child.equals("text:list-item")};
                children(r, inItem -> {
                    if (inItem.equals("text:list")) {
                        list(r, place);
                        labelled[0] = false;
                    } else if (inItem.equals("text:p") || inItem.equals("text:h")) {
                        paragraph(r, new ListPlace(place.list(), place.level(), place.style(), labelled[0]));
                        labelled[0] = false;
                    } else {
                        blockChild(r, inItem, null);
                    }
                });
            } else {
                skip(r);
            }
        });
    }

    private void paragraph(XMLStreamReader r, ListPlace place) throws XMLStreamException {
        flushText();
        int start = document.getLength();
        OdtStyles.Resolved style = styles.paragraph(attribute(r, Odf.TEXT, "style-name"));
        afterSpace = true;
        inline(r, style);
        flushText();
        insert("\n", characterAttributes(style));
        document.setParagraphAttributes(start, Math.max(1, document.getLength() - start),
                paragraphAttributes(style, place), false);
    }

    /** The text of a paragraph or span, with the formatting in effect. */
    private void inline(XMLStreamReader r, OdtStyles.Resolved format) throws XMLStreamException {
        AttributeSet attributes = characterAttributes(format);
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA
                    || event == XMLStreamConstants.SPACE) {
                characters(r.getText(), attributes);
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                String child = name(r);
                switch (child) {
                    case "text:span" -> inline(r, styles.text(format, attribute(r, Odf.TEXT, "style-name")));
                    case "text:s" -> {
                        int count = 1;
                        try {
                            String c = attribute(r, Odf.TEXT, "c");
                            if (c != null) count = Math.max(1, Math.min(10_000, Integer.parseInt(c)));
                        } catch (NumberFormatException ignored) {
                            // one space
                        }
                        add(" ".repeat(count), attributes);
                        skip(r);
                    }
                    case "text:tab" -> {
                        add("\t", attributes);
                        skip(r);
                    }
                    case "text:line-break" -> {
                        add(String.valueOf(WritepadDocument.LINE_BREAK), attributes);
                        skip(r);
                    }
                    default -> {
                        if (SKIPPED.contains(child) || child.startsWith("draw:") || child.startsWith("?:")
                                || child.startsWith("text:bookmark") || child.startsWith("text:reference-mark")
                                || child.equals("text:soft-page-break")) {
                            skip(r);
                        } else {
                            // Links, fields, ruby base: their text.
                            inline(r, format);
                        }
                    }
                }
            }
        }
    }

    /** Character data with ODF's white space rule: runs of spaces, tabs and line ends count as one space. */
    private void characters(String chars, AttributeSet attributes) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                if (!afterSpace) s.append(' ');
                afterSpace = true;
            } else {
                s.append(c);
                afterSpace = false;
            }
        }
        if (!s.isEmpty()) append(s.toString(), attributes);
    }

    /** Text from an element (a space, tab or line break), which is never collapsed. */
    private void add(String s, AttributeSet attributes) {
        append(s, attributes);
        afterSpace = false;
    }

    private void append(String s, AttributeSet attributes) {
        if (textFormat != null && !textFormat.isEqual(attributes)) flushText();
        textFormat = attributes;
        text.append(s);
    }

    private void flushText() {
        if (!text.isEmpty()) insert(text.toString(), textFormat);
        text.setLength(0);
        textFormat = null;
    }

    private void insert(String s, AttributeSet attributes) {
        try {
            document.insertString(document.getLength(), s, attributes);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Every paragraph ends with a line end; the document has its own after the last one. */
    private void removeTrailingParagraphEnd() {
        int length = document.getLength();
        try {
            if (length > 0 && document.getText(length - 1, 1).equals("\n")) document.remove(length - 1, 1);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- formatting

    private SimpleAttributeSet characterAttributes(OdtStyles.Resolved style) {
        Map<String, String> t = style.text;
        SimpleAttributeSet a = new SimpleAttributeSet();
        String face = t.get("style:font-name");
        String family = face != null ? styles.fontFaces.getOrDefault(face, face)
                : t.containsKey("fo:font-family") ? unquote(t.get("fo:font-family")) : null;
        StyleConstants.setFontFamily(a, family != null && !family.isBlank() ? family : Documents.DEFAULT_FAMILY);
        double size = Odf.points(t.get("fo:font-size"));
        StyleConstants.setFontSize(a, Double.isNaN(size) ? Documents.DEFAULT_SIZE : (int) Math.max(1, Math.round(size)));
        String weight = t.getOrDefault("fo:font-weight", "normal");
        if (weight.equals("bold") || weight.matches("[6-9]00")) StyleConstants.setBold(a, true);
        String fontStyle = t.getOrDefault("fo:font-style", "normal");
        if (fontStyle.equals("italic") || fontStyle.equals("oblique")) StyleConstants.setItalic(a, true);
        if (isOn(t.get("style:text-underline-style"))) StyleConstants.setUnderline(a, true);
        if (isOn(t.get("style:text-line-through-style")) || isOn(t.get("style:text-line-through-type"))) {
            StyleConstants.setStrikeThrough(a, true);
        }
        String position = t.get("style:text-position");
        if (position != null) {
            String first = position.strip().split("\\s+")[0];
            double percent = Odf.percent(first);
            if (first.equals("super") || percent > 0) StyleConstants.setSuperscript(a, true);
            if (first.equals("sub") || percent < 0) StyleConstants.setSubscript(a, true);
        }
        String color = t.get("fo:color");
        if (color != null && color.matches("#[0-9a-fA-F]{6}") && !"true".equals(t.get("style:use-window-font-color"))
                && !color.equalsIgnoreCase("#000000")) {
            StyleConstants.setForeground(a, new Color(Integer.parseInt(color.substring(1), 16)));
        }
        return a;
    }

    private static boolean isOn(String lineStyle) {
        return lineStyle != null && !lineStyle.equals("none");
    }

    private SimpleAttributeSet paragraphAttributes(OdtStyles.Resolved style, ListPlace place) {
        Map<String, String> p = style.paragraph;
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setAlignment(a, switch (p.getOrDefault("fo:text-align", "start")) {
            case "center" -> StyleConstants.ALIGN_CENTER;
            case "end", "right" -> StyleConstants.ALIGN_RIGHT;
            case "justify" -> StyleConstants.ALIGN_JUSTIFIED;
            default -> StyleConstants.ALIGN_LEFT;
        });
        double left = length(p.get("fo:margin-left"));
        double first = length(p.get("fo:text-indent"));
        if (place != null) {
            OdtStyles.ListLevel level = place.style() != null ? place.style().get(place.level()) : null;
            ListFormat format = level != null ? level.format() : ListFormat.numberingFor(place.level());
            if (!style.ownMarginLeft) {
                boolean known = level != null && !Double.isNaN(level.marginLeft());
                left = known ? level.marginLeft() : ListAttributes.leftIndent(place.level());
                first = known ? level.textIndent() : -ListAttributes.STEP;
            }
            if (place.labelled() && format != null) {
                ListAttributes.set(a, place.list(), place.level(), format);
            } else {
                first = 0; // the text of an item's further paragraphs lines up with its first
            }
        }
        StyleConstants.setLeftIndent(a, (float) left);
        StyleConstants.setRightIndent(a, (float) length(p.get("fo:margin-right")));
        StyleConstants.setFirstLineIndent(a, (float) first);
        StyleConstants.setSpaceAbove(a, (float) length(p.get("fo:margin-top")));
        StyleConstants.setSpaceBelow(a, (float) length(p.get("fo:margin-bottom")));
        double lineHeight = Odf.percent(p.get("fo:line-height"));
        StyleConstants.setLineSpacing(a, Double.isNaN(lineHeight) ? 0 : (float) Math.max(0, lineHeight / 100 - 1));
        if (!style.tabs.isEmpty()) {
            double base = styles.tabsRelativeToIndent ? left : 0;
            List<TabStop> tabs = new ArrayList<>();
            for (OdtStyles.Tab tab : style.tabs) {
                tabs.add(new TabStop((float) Math.max(0, tab.position() + base), tab.alignment(), tab.leader()));
            }
            tabs.sort((x, y) -> Float.compare(x.getPosition(), y.getPosition()));
            StyleConstants.setTabSet(a, new TabSet(tabs.toArray(TabStop[]::new)));
        }
        return a;
    }

    private static double length(String value) {
        double points = Odf.points(value);
        return Double.isNaN(points) ? 0 : points;
    }

    // ---------------------------------------------------------------- page setup

    private DocumentSettings settings() {
        String master = styles.masterPages.containsKey("Standard") ? "Standard" : styles.firstMasterPage;
        Map<String, String> page = master != null ? styles.pageLayouts.get(styles.masterPages.get(master)) : null;
        if (page == null) return DocumentSettings.DEFAULT;
        double width = Odf.points(page.get("fo:page-width"));
        double height = Odf.points(page.get("fo:page-height"));
        DocumentSettings settings = DocumentSettings.DEFAULT;
        if (width > 0 && height > 0) {
            double shortSide = Math.min(width, height);
            double longSide = Math.max(width, height);
            PaperSize paper = PaperSize.A4;
            double best = Double.MAX_VALUE;
            for (PaperSize candidate : PaperSize.values()) {
                double distance = Math.abs(candidate.width() - shortSide) + Math.abs(candidate.height() - longSide);
                if (distance < best) {
                    best = distance;
                    paper = candidate;
                }
            }
            boolean landscape = "landscape".equals(page.get("style:print-orientation")) || width > height;
            settings = new DocumentSettings(paper, landscape ? Orientation.LANDSCAPE : Orientation.PORTRAIT,
                    Margins.DEFAULT);
        }
        String all = page.get("fo:margin");
        Margins m = settings.margins();
        Margins read = new Margins(
                margin(page.getOrDefault("fo:margin-top", all), m.top()),
                margin(page.getOrDefault("fo:margin-left", all), m.left()),
                margin(page.getOrDefault("fo:margin-bottom", all), m.bottom()),
                margin(page.getOrDefault("fo:margin-right", all), m.right()));
        try {
            return settings.withMargins(read);
        } catch (IllegalArgumentException e) {
            return settings; // margins that leave no room for text
        }
    }

    private static double margin(String value, double fallback) {
        double points = Odf.points(value);
        return Double.isNaN(points) || points < 0 ? fallback : points;
    }
}
