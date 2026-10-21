package eu.amadeos.apps.writepad.service.format.odt;

import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.service.list.ListFormat;

import javax.swing.text.TabStop;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The styles of an ODT file, from {@code styles.xml} and {@code content.xml}, and how they inherit: a style's
 * properties are those of the family's default style, overridden by each parent from the top down and finally
 * by its own. Properties are kept as the attributes read, keyed by prefix and name ({@code fo:font-size}).
 */
final class OdtStyles {
    /** One {@code style:style} or {@code style:default-style}. */
    static final class Style {
        final String name;
        final String parent;
        final Map<String, String> paragraph = new HashMap<>();
        final Map<String, String> text = new HashMap<>();
        /** Tab stops if the style defines them, else null (inherited). */
        List<Tab> tabs;
        String listStyle;

        Style(String name, String parent) {
            this.name = name;
            this.parent = parent;
        }
    }

    /** A tab stop as written: position relative to the paragraph's indent. */
    record Tab(double position, int alignment, int leader) {
    }

    /** One level of a {@code text:list-style}; NaN where the level leaves the indent open. */
    record ListLevel(ListFormat format, double marginLeft, double textIndent) {
    }

    /** The resolved formatting of a paragraph or a run. */
    static final class Resolved {
        final Map<String, String> paragraph = new HashMap<>();
        final Map<String, String> text = new HashMap<>();
        List<Tab> tabs = List.of();
        String listStyle;
        /** True if a style other than the default sets the left indent. */
        boolean ownMarginLeft;

        Resolved copy() {
            Resolved r = new Resolved();
            r.paragraph.putAll(paragraph);
            r.text.putAll(text);
            r.tabs = tabs;
            r.listStyle = listStyle;
            r.ownMarginLeft = ownMarginLeft;
            return r;
        }
    }

    private final Map<String, Style> defaults = new HashMap<>();
    private final Map<String, Style> common = new HashMap<>();
    private final Map<String, Style> automatic = new HashMap<>();
    private final Map<String, Map<Integer, ListLevel>> commonLists = new HashMap<>();
    private final Map<String, Map<Integer, ListLevel>> automaticLists = new HashMap<>();
    /** Font face names to their family. */
    final Map<String, String> fontFaces = new HashMap<>();
    final Map<String, Map<String, String>> pageLayouts = new HashMap<>();
    final Map<String, String> masterPages = new HashMap<>();
    String firstMasterPage;
    /** LibreOffice's setting whether tab positions count from the indent (ODF) or from the page margin. */
    boolean tabsRelativeToIndent = true;

    void addDefault(String family, Style style) {
        defaults.put(family, style);
    }

    void add(String family, Style style, boolean isAutomatic) {
        (isAutomatic ? automatic : common).put(family + ":" + style.name, style);
    }

    void addList(String name, Map<Integer, ListLevel> levels, boolean isAutomatic) {
        (isAutomatic ? automaticLists : commonLists).put(name, levels);
    }

    Map<Integer, ListLevel> list(String name) {
        if (name == null) return null;
        Map<Integer, ListLevel> levels = automaticLists.get(name);
        return levels != null ? levels : commonLists.get(name);
    }

    private Style find(String family, String name) {
        if (name == null) return null;
        Style style = automatic.get(family + ":" + name);
        return style != null ? style : common.get(family + ":" + name);
    }

    /** A paragraph style with everything it inherits, starting from the default style. */
    Resolved paragraph(String name) {
        Resolved r = new Resolved();
        r.text.put("style:font-name", Documents.DEFAULT_FAMILY);
        r.text.put("fo:font-size", Documents.DEFAULT_SIZE + "pt");
        Style defaultStyle = defaults.get("paragraph");
        if (defaultStyle != null) apply(r, defaultStyle, false);
        for (Style style : chain("paragraph", name)) apply(r, style, true);
        return r;
    }

    /** A run's formatting: that of its surroundings with a text style applied. */
    Resolved text(Resolved around, String name) {
        Resolved r = around.copy();
        for (Style style : chain("text", name)) applyText(r, style.text);
        return r;
    }

    private List<Style> chain(String family, String name) {
        List<Style> chain = new ArrayList<>();
        Style style = find(family, name);
        while (style != null && chain.size() < 32 && !chain.contains(style)) {
            chain.add(0, style);
            style = find(family, style.parent);
        }
        return chain;
    }

    private static void apply(Resolved r, Style style, boolean own) {
        Map<String, String> p = style.paragraph;
        String margin = p.get("fo:margin");
        if (margin != null) {
            for (String side : new String[]{"top", "bottom", "left", "right"}) r.paragraph.put("fo:margin-" + side, margin);
        }
        r.paragraph.putAll(p);
        if (own && (p.containsKey("fo:margin-left") || margin != null)) r.ownMarginLeft = true;
        if (style.tabs != null) r.tabs = style.tabs;
        if (style.listStyle != null) r.listStyle = style.listStyle;
        applyText(r, style.text);
    }

    /** Applies text properties; a font size in percent counts from the size so far. */
    private static void applyText(Resolved r, Map<String, String> text) {
        // A style names its font by face or by family; either replaces what it inherits.
        if (text.containsKey("style:font-name") && !text.containsKey("fo:font-family")) r.text.remove("fo:font-family");
        if (text.containsKey("fo:font-family") && !text.containsKey("style:font-name")) r.text.remove("style:font-name");
        // A colour of its own replaces the automatic (window) colour.
        if (text.containsKey("fo:color") && !text.containsKey("style:use-window-font-color")) {
            r.text.remove("style:use-window-font-color");
        }
        for (Map.Entry<String, String> property : text.entrySet()) {
            if (property.getKey().equals("fo:font-size")) {
                double percent = Odf.percent(property.getValue());
                if (!Double.isNaN(percent)) {
                    double size = Odf.points(r.text.get("fo:font-size"));
                    if (Double.isNaN(size)) size = Documents.DEFAULT_SIZE;
                    r.text.put("fo:font-size", Odf.pt(size * percent / 100));
                    continue;
                }
            }
            r.text.put(property.getKey(), property.getValue());
        }
    }
}
