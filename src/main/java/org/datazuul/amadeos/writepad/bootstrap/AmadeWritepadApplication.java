package org.datazuul.amadeos.writepad.bootstrap;

import org.datazuul.amadeos.commons.app.AmadeApplication;
import org.datazuul.amadeos.commons.app.AppInfo;
import org.datazuul.amadeos.writepad.ui.main.MainPresenter;
import org.datazuul.amadeos.writepad.ui.main.MainWindow;

import java.util.List;

public final class AmadeWritepadApplication {
    public static final AppInfo APP = new AppInfo("amade-writepad", "Writepad", "writepad");

    private AmadeWritepadApplication() {
    }

    /** {@code --sample} opens the window with a few pages of sample text. */
    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            if (List.of(args).contains("--sample")) presenter.insertSampleText();
            window.setVisible(true);
        }, new WritepadModule());
    }
}
