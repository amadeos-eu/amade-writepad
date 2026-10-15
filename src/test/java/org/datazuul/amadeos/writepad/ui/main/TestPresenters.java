package org.datazuul.amadeos.writepad.ui.main;

import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.writepad.QueueExecutor;
import org.datazuul.amadeos.writepad.bootstrap.WritepadModule;
import org.datazuul.amadeos.writepad.repository.DocumentFiles;
import org.datazuul.amadeos.writepad.repository.WritepadPreferences;
import org.datazuul.amadeos.writepad.service.DocumentService;
import org.datazuul.amadeos.writepad.service.format.FormatRegistry;
import org.datazuul.amadeos.writepad.service.format.rtf.RtfFormat;
import org.datazuul.amadeos.writepad.service.format.txt.PlainTextFormat;
import org.datazuul.amadeos.writepad.service.layout.PageLayout;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Builds presenters for tests, with RTF and text and one {@link QueueExecutor} for all threads. */
final class TestPresenters {
    static final Messages MSG = Messages.load(WritepadModule.class, WritepadModule.MESSAGES, Locale.ENGLISH);

    private TestPresenters() {
    }

    static FormatRegistry formats() {
        return new FormatRegistry(List.of(new RtfFormat(), new PlainTextFormat()));
    }

    static MainPresenter create(MainView view, PageLayout layout, Path config, QueueExecutor executor) {
        return create(view, layout, config, executor, 1, new WritepadPreferences(config.resolve("writepad.properties")));
    }

    static MainPresenter create(MainView view, PageLayout layout, Path config, QueueExecutor executor,
                                double screenScale, WritepadPreferences preferences) {
        DocumentService documents = new DocumentService(new DocumentFiles(), formats(), executor);
        return new MainPresenter(view, layout, MSG, null, () -> screenScale, preferences, documents, executor);
    }
}
