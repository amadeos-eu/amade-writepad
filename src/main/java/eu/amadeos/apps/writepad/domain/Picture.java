package eu.amadeos.apps.writepad.domain;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * A picture in the text: the image file as it is stored in the document (PNG, JPEG or GIF), its size on the
 * page in points, where it sits, its border and its description (alt text).
 *
 * <p>Pictures are immutable; the {@code with...} methods return changed copies that share the image data.
 * Two pictures are never equal unless they are the same object: the document keeps every picture in an
 * element of its own, and equal attributes would merge two neighbouring pictures into one element.
 */
public final class Picture {
    /** Screen pixels per inch assumed for an image's natural size, as LibreOffice and Word assume it. */
    public static final double PIXELS_PER_INCH = 96;

    /** Where the picture sits: in the line like a character, or at the left or right with text flowing around it. */
    public enum Placement {
        IN_LINE, FLOAT_LEFT, FLOAT_RIGHT;

        public boolean floats() {
            return this != IN_LINE;
        }
    }

    /** The line around the picture. */
    public enum Border {
        NONE(0), THIN(0.75f), THICK(2.25f);

        private final float width;

        Border(float width) {
            this.width = width;
        }

        /** Line width in points. */
        public float width() {
            return width;
        }
    }

    private final byte[] data;
    private final String mimeType;
    private final String name;
    private final int pixelWidth;
    private final int pixelHeight;
    private final double width;
    private final double height;
    private final Placement placement;
    private final Border border;
    private final String description;
    /** The decoded image, shared by all copies; decoded on first use. */
    private final Decoded decoded;

    private static final class Decoded {
        private volatile BufferedImage image;
    }

    private Picture(byte[] data, String mimeType, String name, int pixelWidth, int pixelHeight, double width,
                    double height, Placement placement, Border border, String description, Decoded decoded) {
        this.data = data;
        this.mimeType = mimeType;
        this.name = name;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.width = width;
        this.height = height;
        this.placement = Objects.requireNonNull(placement, "placement");
        this.border = Objects.requireNonNull(border, "border");
        this.description = description == null ? "" : description;
        this.decoded = decoded;
    }

    /**
     * A picture from an image file at its natural size, in the line, without border. PNG, JPEG and GIF are kept
     * as they are; anything else Java can read (BMP, WBMP, TIFF) is stored as PNG, which every format can hold.
     *
     * @param name the file name, used for the name inside the document and for "Save Picture As"
     * @throws IOException if the bytes are no image Java can read
     */
    public static Picture of(byte[] bytes, String name) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IOException("Not a picture WritePad can read: " + name);
        String mime = mimeType(bytes);
        byte[] data = bytes;
        String fileName = name == null || name.isBlank() ? "picture" : name;
        if (mime == null) {
            data = png(image);
            mime = "image/png";
            fileName = withExtension(fileName, "png");
        }
        Decoded decoded = new Decoded();
        decoded.image = image;
        double w = image.getWidth() * Units.POINTS_PER_INCH / PIXELS_PER_INCH;
        double h = image.getHeight() * Units.POINTS_PER_INCH / PIXELS_PER_INCH;
        return new Picture(data, mime, fileName, image.getWidth(), image.getHeight(), w, h, Placement.IN_LINE,
                Border.NONE, "", decoded);
    }

    /** A picture from an image already on the screen, such as a screenshot on the clipboard: stored as PNG. */
    public static Picture of(BufferedImage image, String name) throws IOException {
        return of(png(image), withExtension(name == null ? "picture" : name, "png"));
    }

    /** "image/png", "image/jpeg" or "image/gif" from the file's first bytes; null for other kinds. */
    public static String mimeType(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "image/gif";
        return null;
    }

    /** Whether a name looks like an image's file name ({@code valley.jpg}), not like "Picture 1". */
    public static boolean isFileName(String name) {
        return name != null && !name.isBlank() && name.toLowerCase(java.util.Locale.ROOT)
                .matches(".+\\.(png|jpe?g|gif|bmp|tiff?|webp)");
    }

    /** The file extension that goes with the stored data: png, jpg or gif. */
    public String extension() {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) throw new IOException("Cannot store the picture as PNG");
        return out.toByteArray();
    }

    private static String withExtension(String name, String extension) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base + "." + extension;
    }

    // ---------------------------------------------------------------- properties

    /** The image file's bytes; do not change them. */
    public byte[] data() {
        return data;
    }

    public String mimeType() {
        return mimeType;
    }

    public String name() {
        return name;
    }

    public int pixelWidth() {
        return pixelWidth;
    }

    public int pixelHeight() {
        return pixelHeight;
    }

    /** Width on the page in points, without the border. */
    public double width() {
        return width;
    }

    /** Height on the page in points, without the border. */
    public double height() {
        return height;
    }

    /** Width at {@link #PIXELS_PER_INCH}. */
    public double naturalWidth() {
        return pixelWidth * Units.POINTS_PER_INCH / PIXELS_PER_INCH;
    }

    public double naturalHeight() {
        return pixelHeight * Units.POINTS_PER_INCH / PIXELS_PER_INCH;
    }

    /** Height divided by width of the image. */
    public double aspect() {
        return pixelWidth == 0 ? 1 : (double) pixelHeight / pixelWidth;
    }

    public Placement placement() {
        return placement;
    }

    public Border border() {
        return border;
    }

    public String description() {
        return description;
    }

    /** The decoded image; null if the data cannot be decoded (a broken file). */
    public BufferedImage image() {
        BufferedImage image = decoded.image;
        if (image == null) {
            try {
                image = ImageIO.read(new ByteArrayInputStream(data));
            } catch (IOException | RuntimeException e) {
                image = null;
            }
            decoded.image = image;
        }
        return image;
    }

    // ---------------------------------------------------------------- copies

    /** The same picture at another size; sizes below 2 points become 2 points. */
    public Picture withSize(double newWidth, double newHeight) {
        return new Picture(data, mimeType, name, pixelWidth, pixelHeight, Math.max(2, newWidth),
                Math.max(2, newHeight), placement, border, description, decoded);
    }

    /** The same picture at a width, its height following the proportions. */
    public Picture withWidth(double newWidth) {
        double w = Math.max(2, newWidth);
        return withSize(w, w * aspect());
    }

    /** Scaled down, keeping the proportions, so that it fits into the given box; unchanged if it fits. */
    public Picture fittedInto(double maxWidth, double maxHeight) {
        double scale = Math.min(1, Math.min(maxWidth / width, maxHeight / height));
        return scale >= 1 ? this : withSize(width * scale, height * scale);
    }

    public Picture withPlacement(Placement newPlacement) {
        return new Picture(data, mimeType, name, pixelWidth, pixelHeight, width, height, newPlacement, border,
                description, decoded);
    }

    public Picture withBorder(Border newBorder) {
        return new Picture(data, mimeType, name, pixelWidth, pixelHeight, width, height, placement, newBorder,
                description, decoded);
    }

    public Picture withDescription(String newDescription) {
        return new Picture(data, mimeType, name, pixelWidth, pixelHeight, width, height, placement, border,
                newDescription, decoded);
    }

    /**
     * Another image in this picture's place: same width, placement, border and description, the height
     * following the new image's proportions.
     */
    public Picture replacedBy(Picture other) {
        Picture p = new Picture(other.data, other.mimeType, other.name, other.pixelWidth, other.pixelHeight, width,
                width * other.aspect(), placement, border, description, other.decoded);
        return p;
    }

    /** A picture read from a file, with the size, placement, border and description the file gives. */
    public Picture withLayout(double newWidth, double newHeight, Placement newPlacement, Border newBorder,
                              String newDescription) {
        return new Picture(data, mimeType, name, pixelWidth, pixelHeight, Math.max(2, newWidth),
                Math.max(2, newHeight), newPlacement, newBorder, newDescription, decoded);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Picture[%s %dx%d px, %.1fx%.1f pt, %s]", name, pixelWidth, pixelHeight,
                width, height, placement);
    }
}
