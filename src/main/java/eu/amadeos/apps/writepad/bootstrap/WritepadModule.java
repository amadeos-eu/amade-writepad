package eu.amadeos.apps.writepad.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.MeasurementUnit;
import eu.amadeos.apps.writepad.service.DocumentService;
import eu.amadeos.apps.writepad.service.format.DocumentFormat;
import eu.amadeos.apps.writepad.service.format.FormatRegistry;
import eu.amadeos.apps.writepad.service.format.doc.DocFormat;
import eu.amadeos.apps.writepad.service.format.docx.DocxFormat;
import eu.amadeos.apps.writepad.service.format.odt.OdtFormat;
import eu.amadeos.apps.writepad.service.format.rtf.RtfFormat;
import eu.amadeos.apps.writepad.service.format.txt.PlainTextFormat;
import eu.amadeos.apps.writepad.service.layout.PageLayout;
import eu.amadeos.apps.writepad.service.print.PrintService;
import eu.amadeos.apps.writepad.ui.editor.PageEditor;
import eu.amadeos.apps.writepad.ui.main.MainView;
import eu.amadeos.apps.writepad.ui.main.MainWindow;
import eu.amadeos.apps.writepad.ui.main.ScreenScale;

import java.util.Locale;
import java.util.function.DoubleSupplier;

/**
 * amade-writepad bindings. The background executor, theme, language and the other shared services come
 * from {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class WritepadModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.writepad.messages";

    @Override
    protected void configure() {
        bind(PrintService.class).in(Scopes.SINGLETON);
        // --8<-- [start:formats]
        // The formats files can be opened and saved in; the registry makes the first that keeps formatting
        // (ODT) the default for new documents.
        Multibinder<DocumentFormat> formats = Multibinder.newSetBinder(binder(), DocumentFormat.class);
        formats.addBinding().to(OdtFormat.class);
        formats.addBinding().to(DocxFormat.class);
        formats.addBinding().to(DocFormat.class);
        formats.addBinding().to(RtfFormat.class);
        formats.addBinding().to(PlainTextFormat.class);
        // --8<-- [end:formats]
        bind(FormatRegistry.class).in(Scopes.SINGLETON);
        bind(DocumentService.class).in(Scopes.SINGLETON);
        bind(MainWindow.class).in(Scopes.SINGLETON);
        bind(MainView.class).to(MainWindow.class);
    }

    @Provides
    @Singleton
    @ScreenScale
    DoubleSupplier screenScale() {
        double scale = PageEditor.screenScale();
        return () -> scale;
    }

    /** Page setup of the open document, at true size. */
    @Provides
    @Singleton
    PageLayout pageLayout(@ScreenScale DoubleSupplier screenScale) {
        return new PageLayout(DocumentSettings.DEFAULT, screenScale.getAsDouble());
    }

    /** Centimetres or inches, from the region the user lives in. */
    @Provides
    @Singleton
    MeasurementUnit measurementUnit() {
        return MeasurementUnit.fromEnvironment(System.getenv(), Locale.getDefault(Locale.Category.FORMAT));
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(WritepadModule.class, MESSAGES, locale);
    }
}
