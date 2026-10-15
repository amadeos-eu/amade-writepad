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

    /** Opens the file named as first argument; {@code --sample} shows a few pages of sample text instead. */
    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(List.of(args));
        }, new WritepadModule());
    }
}
