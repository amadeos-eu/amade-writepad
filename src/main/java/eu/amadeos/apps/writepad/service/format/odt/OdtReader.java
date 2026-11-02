package eu.amadeos.apps.writepad.service.format.odt;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.Margins;
import eu.amadeos.apps.writepad.domain.Orientation;
import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.domain.Picture;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.picture.PictureAttributes;
import eu.amadeos.apps.writepad.service.list.ListAttributes;
import eu.amadeos.apps.writepad.domain.TableFormat;
import eu.amadeos.apps.writepad.service.table.TableAttributes;
import eu.amadeos.apps.writepad.service.table.TableBuilder;
import eu.amadeos.apps.writepad.service.table.TableLines;
import eu.amadeos.apps.writepad.service.list.ListFormat;
import eu.amadeos.commons.print.PaperSize;

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
 * text; pictures become pictures (in the line, or floating with the text around them); other frames, notes and
 * comments are skipped.
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
            "text:bibliography-source", "office:event-listeners");

    private final OdtStyles styles = new OdtStyles();
    private final StyledDocument document = Documents.newDocument();
    /** Our list ids for the lists' xml:id, and the last list of each list style. */
    private final Map<String, Integer> listsByXmlId = new HashMap<>();
    private final Map<String, Integer> lastListByStyle = new HashMap<>();
    private int lastList;
    private int nextListId = 1;
    /** How many tables the reader is in; tables in tables come in as paragraphs. */
    private int tableDepth;

    /** Text waiting to be inserted, all in the same format. */
    private final StringBuilder text = new StringBuilder();
    private AttributeSet textFormat;
    /** ODF collapses white space: true after a space and at the start of a paragraph. */
    private boolean afterSpace;

    /** Where a paragraph sits in a list. */
    private record ListPlace(int list, int level, Map<Integer, OdtStyles.ListLevel> style, boolean labelled) {
    }

    /** The files of the package, for the pictures. */
    private final Map<String, byte[]> files;

    private OdtReader(Map<String, byte[]> files) {
        this.files = files;
    }

    static DocumentContent read(byte[] bytes) throws IOException {
        Map<String, byte[]> files = unzip(bytes);
        byte[] content = files.get("content.xml");
        if (content == null) throw new IOException("Not an OpenDocument file: no content.xml");
        OdtReader reader = new OdtReader(files);
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
                if (name.equals("content.xml") || name.equals("styles.xml") || name.equals("settings.xml")
                        || name.startsWith("Pictures/") && !entry.isDirectory()) {
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
                case "style:graphic-properties" -> {
                    style.graphic.putAll(attributes(r));
                    skip(r);
                }
                case "style:table-properties", "style:table-column-properties", "style:table-row-properties",
                     "style:table-cell-properties" -> {
                    style.table.putAll(attributes(r));
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
        } else if (child.equals("table:table") && tableDepth == 0) {
            table(r);
        } else if (SKIPPED.contains(child) || child.startsWith("draw:") || child.startsWith("?:")
                || child.equals("table:table-column") || child.equals("table:table-columns")) {
            skip(r);
        } else {
            // Sections, indexes, tables in tables: their paragraphs are kept.
            block(r, null);
        }
    }

    // --8<-- [start:table]
    /**
     * A table: its paragraphs go into the document cell by cell, and at its end they become a table. Merged
     * cells are split again (the text stays in the first one), a table inside a cell comes in as its
     * paragraphs, and the heading rows repeat if the file puts them into {@code table:table-header-rows}.
     */
    private void table(XMLStreamReader r) throws XMLStreamException {
        flushText();
        tableDepth++;
        TableBuilder builder = new TableBuilder(document);
        List<Double> widths = new ArrayList<>();
        Map<String, String> tableProps = styles.table("table", attribute(r, Odf.TABLE, "style-name"));
        boolean[] header = {false};
        List<Map<String, String>> firstCells = new ArrayList<>();
        children(r, child -> tablePart(r, child, builder, widths, header, firstCells, false));
        tableDepth--;
        TableFormat format = tableFormat(widths, builder.columns(), tableProps, firstCells, builder.rows())
                .withRepeatHeader(header[0]);
        builder.finish(format);
    }

    private void tablePart(XMLStreamReader r, String child, TableBuilder builder, List<Double> widths,
                           boolean[] header, List<Map<String, String>> cells, boolean inHeader)
            throws XMLStreamException {
        switch (child) {
            case "table:table-column" -> {
                Map<String, String> column = styles.table("table-column", attribute(r, Odf.TABLE, "style-name"));
                int repeated = repeat(attribute(r, Odf.TABLE, "number-columns-repeated"));
                double w = Odf.points(column.get("style:column-width"));
                for (int i = 0; i < repeated; i++) widths.add(w);
                skip(r);
            }
            case "table:table-columns", "table:table-column-group", "table:table-rows", "table:table-row-group" ->
                    children(r, c -> tablePart(r, c, builder, widths, header, cells, inHeader));
            case "table:table-header-rows" -> {
                if (builder.rows() == 0) header[0] = true;
                children(r, c -> tablePart(r, c, builder, widths, header, cells, true));
            }
            case "table:table-row" -> {
                int repeated = Math.min(repeat(attribute(r, Odf.TABLE, "number-rows-repeated")), 100);
                int first = builder.rows();
                builder.row();
                children(r, c -> tableCell(r, c, builder, cells));
                // A repeated row is copied as empty cells: LibreOffice repeats only rows without text.
                int columns = builder.cellsInRow();
                for (int i = 1; i < repeated; i++) {
                    builder.row();
                    for (int k = 0; k < columns; k++) builder.emptyCell(characterAttributes(styles.paragraph(null)));
                }
                if (inHeader && first > 0) header[0] = false;
            }
            default -> skip(r);
        }
    }

    private void tableCell(XMLStreamReader r, String child, TableBuilder builder, List<Map<String, String>> cells)
            throws XMLStreamException {
        boolean covered = child.equals("table:covered-table-cell");
        if (!covered && !child.equals("table:table-cell")) {
            skip(r);
            return;
        }
        int repeated = Math.min(repeat(attribute(r, Odf.TABLE, "number-columns-repeated")), 64);
        Map<String, String> props = covered ? Map.of()
                : styles.table("table-cell", attribute(r, Odf.TABLE, "style-name"));
        if (cells.size() < 4 && !covered) cells.add(props);
        AttributeSet empty = characterAttributes(styles.paragraph(null));
        if (covered) {
            skip(r);
        } else {
            builder.cellStart();
            block(r, null);
            flushText();
        }
        String color = props.get("fo:background-color");
        java.awt.Color background = color != null && color.matches("#[0-9a-fA-F]{6}")
                ? new java.awt.Color(Integer.parseInt(color.substring(1), 16)) : null;
        TableAttributes.VerticalAlignment v = switch (props.getOrDefault("style:vertical-align", "top")) {
            case "middle" -> TableAttributes.VerticalAlignment.CENTER;
            case "bottom" -> TableAttributes.VerticalAlignment.BOTTOM;
            default -> TableAttributes.VerticalAlignment.TOP;
        };
        if (covered) builder.emptyCell(empty);
        else builder.cellEnd(background, v, empty);
        for (int i = 1; i < repeated; i++) builder.emptyCell(empty);
    }

    private static int repeat(String value) {
        try {
            return value == null ? 1 : Math.max(1, Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * The table's format from the file: the column widths (or the table's width shared out), alignment, and
     * lines, line width, colour and padding from the first cells' borders.
     */
    private TableFormat tableFormat(List<Double> widths, int columns, Map<String, String> table,
                                    List<Map<String, String>> cells, int rows) {
        int n = Math.max(1, Math.max(columns, widths.size()));
        double total = Odf.points(table.get("style:width"));
        double known = 0;
        int unknown = 0;
        for (double w : widths) {
            if (Double.isNaN(w)) unknown++;
            else known += w;
        }
        unknown += Math.max(0, n - widths.size());
        double fill = Double.isNaN(total) || total <= known ? (known > 0 ? known / Math.max(1, n - unknown) : 0)
                : (total - known) / Math.max(1, unknown);
        if (fill <= 0) fill = (settings().contentWidth()) / n;
        double[] w = new double[n];
        for (int c = 0; c < n; c++) {
            w[c] = c < widths.size() && !Double.isNaN(widths.get(c)) ? widths.get(c) : fill;
        }
        TableFormat format = TableFormat.of(w).withAlignment(switch (table.getOrDefault("table:align", "left")) {
            case "center" -> TableFormat.Alignment.CENTER;
            case "right" -> TableFormat.Alignment.RIGHT;
            default -> TableFormat.Alignment.LEFT;
        });
        Map<String, String> first = cells.isEmpty() ? Map.of() : cells.getFirst();
        Map<String, String> second = cells.size() > 1 ? cells.get(1) : Map.of();
        String left = border(first, "left");
        String right = border(first, "right");
        String top = border(first, "top");
        String bottom = border(first, "bottom");
        boolean innerVertical = columns > 1 && (isOn(right) || isOn(border(second, "left")));
        boolean innerHorizontal = rows > 1 && isOn(bottom);
        format = format.withLines(TableLines.guess(innerVertical, innerHorizontal, isOn(left), isOn(top)));
        for (String line : new String[]{left, top, right, bottom}) {
            if (!isOn(line)) continue;
            for (String part : line.strip().split("\\s+")) {
                double points = Odf.points(part);
                if (!Double.isNaN(points)) format = format.withLineWidth((float) points);
                if (part.matches("#[0-9a-fA-F]{6}")) {
                    format = format.withLineColor(new java.awt.Color(Integer.parseInt(part.substring(1), 16)));
                }
            }
            break;
        }
        double padding = Odf.points(first.getOrDefault("fo:padding-left", first.get("fo:padding")));
        if (!Double.isNaN(padding)) format = format.withPadding(padding);
        return format;
    }

    private static String border(Map<String, String> cell, String side) {
        String own = cell.get("fo:border-" + side);
        return own != null ? own : cell.get("fo:border");
    }
    // --8<-- [end:table]

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
                    case "draw:frame" -> frame(r, attributes);
                    case "draw:a" -> inline(r, format);
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

    // --8<-- [start:frame]
    /**
     * A frame with an image: a picture in the line if it is anchored as a character, otherwise a picture that
     * floats at the side its style puts it (when text runs around it) or sits in the line at its anchor. Frames
     * without an image (text boxes, objects) are skipped.
     */
    private void frame(XMLStreamReader r, AttributeSet around) throws XMLStreamException {
        String anchor = attribute(r, Odf.TEXT, "anchor-type");
        Map<String, String> graphic = styles.graphic(attribute(r, Odf.DRAW, "style-name"));
        double width = Odf.points(attribute(r, Odf.SVG, "width"));
        double height = Odf.points(attribute(r, Odf.SVG, "height"));
        String name = attribute(r, Odf.DRAW, "name");
        String[] href = {null};
        byte[][] inlineData = {null};
        StringBuilder description = new StringBuilder();
        children(r, child -> {
            switch (child) {
                case "draw:image" -> {
                    if (href[0] == null && inlineData[0] == null) {
                        href[0] = r.getAttributeValue(Odf.XLINK, "href");
                        children(r, inner -> {
                            if (inner.equals("office:binary-data")) {
                                try {
                                    inlineData[0] = java.util.Base64.getMimeDecoder().decode(r.getElementText());
                                } catch (IllegalArgumentException e) {
                                    inlineData[0] = null;
                                }
                            } else {
                                skip(r);
                            }
                        });
                    } else {
                        skip(r);
                    }
                }
                case "svg:desc" -> description.append(r.getElementText().strip());
                case "svg:title" -> {
                    String title = r.getElementText().strip();
                    if (description.isEmpty()) description.append(title);
                }
                default -> skip(r);
            }
        });
        byte[] data = inlineData[0] != null ? inlineData[0] : href[0] != null ? files.get(stripDot(href[0])) : null;
        if (data == null) return;
        Picture picture;
        try {
            // The frame's name is the picture's file name when amade-writepad wrote it.
            String file = Picture.isFileName(name) ? name
                    : href[0] != null ? href[0].substring(href[0].lastIndexOf('/') + 1) : "picture.png";
            picture = Picture.of(data, file);
        } catch (java.io.IOException e) {
            return; // an image Java cannot read (SVG, EMF): left out as before
        }
        Picture.Border border = border(graphic);
        double frame = 2 * border.width();
        double w = Double.isNaN(width) ? picture.width() : Math.max(2, width - frame);
        double h = Double.isNaN(height) ? w * picture.aspect() : Math.max(2, height - frame);
        Picture.Placement placement = Picture.Placement.IN_LINE;
        String wrap = graphic.getOrDefault("style:wrap", "none");
        if (!"as-char".equals(anchor) && !"char".equals(anchor) || "char".equals(anchor) && !wrap.equals("none")) {
            if (!wrap.equals("none") && !wrap.equals("run-through")) {
                placement = "right".equals(graphic.get("style:horizontal-pos")) || wrap.equals("left")
                        ? Picture.Placement.FLOAT_RIGHT : Picture.Placement.FLOAT_LEFT;
            }
        }
        flushText();
        insert(String.valueOf(PictureAttributes.OBJECT), PictureAttributes.attributes(around,
                picture.withLayout(w, h, placement, border, description.toString())));
        afterSpace = false;
    }

    private static String stripDot(String href) {
        return href.startsWith("./") ? href.substring(2) : href;
    }

    /** The border of a frame style: none, thin (up to 1 pt) or thick. */
    private static Picture.Border border(Map<String, String> graphic) {
        String border = graphic.getOrDefault("fo:border", graphic.get("fo:border-top"));
        if (border == null || border.isBlank() || border.strip().equals("none")) return Picture.Border.NONE;
        for (String part : border.strip().split("\\s+")) {
            double points = Odf.points(part);
            if (!Double.isNaN(points)) return points > 1.2 ? Picture.Border.THICK : Picture.Border.THIN;
        }
        return Picture.Border.THIN;
    }
    // --8<-- [end:frame]

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
