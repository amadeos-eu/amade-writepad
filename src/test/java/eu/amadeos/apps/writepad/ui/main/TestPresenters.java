package eu.amadeos.apps.writepad.ui.main;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.apps.writepad.QueueExecutor;
import eu.amadeos.apps.writepad.bootstrap.WritepadModule;
import eu.amadeos.apps.writepad.repository.DocumentFiles;
import eu.amadeos.apps.writepad.repository.WritepadPreferences;
import eu.amadeos.apps.writepad.service.DocumentService;
import eu.amadeos.apps.writepad.service.format.FormatRegistry;
import eu.amadeos.apps.writepad.service.format.odt.OdtFormat;
import eu.amadeos.apps.writepad.service.format.rtf.RtfFormat;
import eu.amadeos.apps.writepad.service.format.txt.PlainTextFormat;
import eu.amadeos.apps.writepad.service.layout.PageLayout;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Builds presenters for tests, with RTF and text and one {@link QueueExecutor} for all threads. */
final class TestPresenters {
    static final Messages MSG = Messages.load(WritepadModule.class, WritepadModule.MESSAGES, Locale.ENGLISH);

    private TestPresenters() {
    }

    static FormatRegistry formats() {
        return new FormatRegistry(List.of(new OdtFormat(), new RtfFormat(), new PlainTextFormat()));
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
