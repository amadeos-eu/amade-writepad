package eu.amadeos.writepad.service.print;

import eu.amadeos.writepad.domain.DocumentSettings;
import eu.amadeos.writepad.domain.Orientation;
import eu.amadeos.writepad.service.layout.PagedSectionView;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.util.Objects;

/**
 * The pages of a document for {@link java.awt.print.PrinterJob}: every page is painted by the same views
 * as on screen, without zoom, so lines and page breaks are the same on paper.
 *
 * <p>Swing views must only be used on the event dispatch thread; print on it.
 */
public final class DocumentPageable implements Pageable, Printable {
    private final PagedSectionView section;
    private final int pages;
    private final PageFormat format;

    /** The section must be laid out; the page count is fixed for this print job. */
    public DocumentPageable(PagedSectionView section) {
        this.section = Objects.requireNonNull(section, "section");
        this.pages = section.pageCount();
        this.format = pageFormat(section.pageLayout().settings());
    }

    /**
     * A page format for the settings with the whole sheet as imageable area: the margins are part of the
     * layout, and the printer clips what it cannot reach.
     */
    public static PageFormat pageFormat(DocumentSettings settings) {
        Paper paper = new Paper();
        double width = settings.paper().width();
        double height = settings.paper().height();
        paper.setSize(width, height);
        paper.setImageableArea(0, 0, width, height);
        PageFormat format = new PageFormat();
        format.setPaper(paper);
        format.setOrientation(settings.orientation() == Orientation.LANDSCAPE ? PageFormat.LANDSCAPE
                : PageFormat.PORTRAIT);
        return format;
    }

    @Override
    public int getNumberOfPages() {
        return pages;
    }

    @Override
    public PageFormat getPageFormat(int pageIndex) {
        check(pageIndex);
        return format;
    }

    @Override
    public Printable getPrintable(int pageIndex) {
        check(pageIndex);
        return this;
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pages) return NO_SUCH_PAGE;
        // For a landscape format the printer graphics are already rotated: (0, 0) is the top left corner
        // of the sheet as the reader holds it.
        section.paintPage((Graphics2D) graphics, pageIndex);
        return PAGE_EXISTS;
    }

    private void check(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pages) throw new IndexOutOfBoundsException("No page " + pageIndex);
    }
}
