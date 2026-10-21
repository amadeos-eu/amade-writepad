package eu.amadeos.apps.writepad.service.layout;

import eu.amadeos.apps.writepad.domain.DocumentSettings;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Geometry of the page view in layout space: points (1/72 inch), before zoom.
 *
 * <p>Pages are stacked vertically with {@link #GAP} between them and around them. Page {@code i} starts at
 * {@code y = GAP + i * (pageHeight + GAP)}, at {@code x = GAP}. The text of a page runs between
 * {@link #contentTop(int)} and {@link #contentBottom(int)}. The screen shows layout space multiplied by
 * {@link #zoom()}; printing uses it unscaled, so lines break the same way on screen and on paper.
 */
public final class PageLayout {
    /** Space between pages and around them, in points. */
    public static final int GAP = 18;
    public static final double MIN_ZOOM = 0.25;
    public static final double MAX_ZOOM = 4;

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private DocumentSettings settings;
    private double zoom;

    public PageLayout(DocumentSettings settings, double zoom) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.zoom = checkZoom(zoom);
    }

    public DocumentSettings settings() {
        return settings;
    }

    public void setSettings(DocumentSettings newSettings) {
        Objects.requireNonNull(newSettings, "settings");
        if (newSettings.equals(settings)) return;
        settings = newSettings;
        fire();
    }

    /** Screen pixels per point. */
    public double zoom() {
        return zoom;
    }

    public void setZoom(double newZoom) {
        checkZoom(newZoom);
        if (newZoom == zoom) return;
        zoom = newZoom;
        fire();
    }

    /** Runs after every change of settings or zoom. */
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public int pageWidth() {
        return (int) Math.round(settings.pageWidth());
    }

    public int pageHeight() {
        return (int) Math.round(settings.pageHeight());
    }

    /** Width of the text column. */
    public int contentWidth() {
        return pageWidth() - left() - right();
    }

    public int left() {
        return (int) Math.round(settings.margins().left());
    }

    public int right() {
        return (int) Math.round(settings.margins().right());
    }

    /** Width of the whole page view: one page with gaps on both sides. */
    public int totalWidth() {
        return pageWidth() + 2 * GAP;
    }

    /** Height of the whole page view for the given number of pages. */
    public int totalHeight(int pages) {
        return GAP + Math.max(1, pages) * (pageHeight() + GAP);
    }

    public int pageTop(int page) {
        return GAP + page * (pageHeight() + GAP);
    }

    public int contentTop(int page) {
        return pageTop(page) + (int) Math.round(settings.margins().top());
    }

    public int contentBottom(int page) {
        return pageTop(page) + pageHeight() - (int) Math.round(settings.margins().bottom());
    }

    /** The page whose area (including its bottom gap) contains y. */
    public int pageAt(double y) {
        return Math.max(0, (int) Math.floor((y - GAP) / (pageHeight() + GAP)));
    }

    // --8<-- [start:place]
    /**
     * Where a line of the given height that would start at y goes: unchanged if it fits on its page,
     * otherwise at the top of the text of the next page. Lines starting in a margin or gap move to the next
     * text area. A line taller than a whole page stays where it is, so that layout always terminates.
     */
    public int place(int y, int height) {
        int page = pageAt(y);
        if (y < contentTop(page)) return contentTop(page);
        if (y + height <= contentBottom(page)) return y;
        if (y == contentTop(page)) return y;
        return contentTop(page + 1);
    }
    // --8<-- [end:place]

    /** Number of pages a layout ending at y (exclusive) needs. */
    public int pagesUpTo(int y) {
        if (y <= contentTop(0)) return 1;
        return pageAt(y - 1) + 1;
    }

    private static double checkZoom(double zoom) {
        if (!(zoom >= MIN_ZOOM && zoom <= MAX_ZOOM)) {
            throw new IllegalArgumentException("Zoom out of range: " + zoom);
        }
        return zoom;
    }

    private void fire() {
        for (Runnable listener : listeners) listener.run();
    }
}
