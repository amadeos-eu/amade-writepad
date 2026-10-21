package eu.amadeos.apps.writepad.service.layout;

import eu.amadeos.apps.writepad.service.format.WritepadDocument;

import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import java.util.Objects;

/**
 * Styled text on pages: the views of {@link StyledEditorKit}, with the section and paragraphs replaced by
 * {@link PagedSectionView} and {@link PagedParagraphView}. One kit belongs to one editor, because it carries
 * that editor's {@link PageLayout}.
 */
public final class PagedEditorKit extends StyledEditorKit {
    private final PageLayout layout;
    private final ViewFactory factory = new Factory();

    public PagedEditorKit(PageLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    public PageLayout pageLayout() {
        return layout;
    }

    @Override
    public ViewFactory getViewFactory() {
        return factory;
    }

    /** An editor without a loaded file gets the same kind of document as a new one. */
    @Override
    public Document createDefaultDocument() {
        return new WritepadDocument();
    }

    @Override
    public Object clone() {
        return new PagedEditorKit(layout);
    }

    // --8<-- [start:factory]
    private final class Factory implements ViewFactory {
        @Override
        public View create(Element element) {
            String kind = element.getName();
            if (kind == null) return new LabelView(element);
            return switch (kind) {
                case AbstractDocument.SectionElementName -> new PagedSectionView(element, layout);
                case AbstractDocument.ParagraphElementName -> new PagedParagraphView(element, layout);
                case AbstractDocument.ContentElementName -> WritepadDocument.isLineBreak(element.getAttributes())
                        ? new LineBreakView(element) : new LabelView(element);
                case StyleConstants.ComponentElementName -> new ComponentView(element);
                case StyleConstants.IconElementName -> new IconView(element);
                default -> new BoxView(element, View.Y_AXIS);
            };
        }
    }
    // --8<-- [end:factory]
}
