package org.datazuul.amadeos.writepad.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.service.layout.PageLayout;
import org.datazuul.amadeos.writepad.service.print.PrintService;
import org.datazuul.amadeos.writepad.ui.editor.PageEditor;
import org.datazuul.amadeos.writepad.ui.main.MainView;
import org.datazuul.amadeos.writepad.ui.main.MainWindow;
import org.datazuul.amadeos.writepad.ui.main.ScreenScale;

import java.util.Locale;
import java.util.function.DoubleSupplier;

/**
 * amade-writepad bindings. The background executor, theme, language and the other shared services come
 * from {@link org.datazuul.amadeos.commons.app.CommonsModule}.
 */
public final class WritepadModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "org.datazuul.amadeos.writepad.messages";

    @Override
    protected void configure() {
        bind(PrintService.class).in(Scopes.SINGLETON);
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

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(WritepadModule.class, MESSAGES, locale);
    }
}
