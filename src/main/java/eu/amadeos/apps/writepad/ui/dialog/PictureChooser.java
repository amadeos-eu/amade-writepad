package eu.amadeos.apps.writepad.ui.dialog;

import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The file choosers for pictures: opening shows a preview of the selected image and starts in the last picture
 * folder (at first the user's Pictures folder); saving suggests the picture's own name.
 */
public final class PictureChooser {
    private static final int PREVIEW = 180;
    private static final String[] EXTENSIONS = {"png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff"};

    private final Messages msg;
    private File lastDirectory;

    public PictureChooser(Messages msg) {
        this.msg = msg;
        Path pictures = XdgDirectories.system().pictures();
        if (Files.isDirectory(pictures)) lastDirectory = pictures.toFile();
    }

    public Optional<Path> chooseOpen(Component parent) {
        EdtExecutor.requireEdt();
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle(msg.get("dialog.insert-picture.title"));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter images = new FileNameExtensionFilter(msg.get("filter.pictures"), EXTENSIONS);
        chooser.addChoosableFileFilter(images);
        chooser.setFileFilter(images);
        JLabel preview = new JLabel("", SwingConstants.CENTER);
        preview.setPreferredSize(new Dimension(PREVIEW, PREVIEW));
        preview.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        chooser.setAccessory(preview);
        chooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY,
                e -> preview.setIcon(thumbnail(chooser.getSelectedFile())));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        File file = chooser.getSelectedFile();
        lastDirectory = file.getParentFile();
        return Optional.of(file.toPath());
    }

    public Optional<Path> chooseSave(Component parent, String suggestedName) {
        EdtExecutor.requireEdt();
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle(msg.get("dialog.save-picture.title"));
        chooser.setSelectedFile(new File(lastDirectory, suggestedName));
        while (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file.exists() && JOptionPane.showConfirmDialog(parent,
                    msg.format("dialog.overwrite.message", file.getName()), msg.get("dialog.overwrite.title"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                continue;
            }
            lastDirectory = file.getParentFile();
            return Optional.of(file.toPath());
        }
        return Optional.empty();
    }

    private static ImageIcon thumbnail(File file) {
        if (file == null || !file.isFile() || file.length() > 64L * 1024 * 1024) return null;
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) return null;
            double scale = Math.min(1, Math.min((double) PREVIEW / image.getWidth(), (double) PREVIEW / image.getHeight()));
            return new ImageIcon(image.getScaledInstance(Math.max(1, (int) (image.getWidth() * scale)),
                    Math.max(1, (int) (image.getHeight() * scale)), Image.SCALE_SMOOTH));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
