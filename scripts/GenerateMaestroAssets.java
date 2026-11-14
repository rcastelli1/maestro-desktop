import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Creates the desktop icon from the geometry in the supplied Maestro artwork. */
public final class GenerateMaestroAssets {
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Informe a pasta dos assets.");
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        List<byte[]> images = new ArrayList<>();
        for (int size : SIZES) {
            BufferedImage image = render(size);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            images.add(png.toByteArray());
            if (size == 256) Files.write(output.resolve("maestro-icon.png"), png.toByteArray());
        }
        try (DataOutputStream ico = new DataOutputStream(Files.newOutputStream(output.resolve("maestro.ico")))) {
            littleShort(ico, 0);
            littleShort(ico, 1);
            littleShort(ico, SIZES.length);
            int offset = 6 + 16 * SIZES.length;
            for (int i = 0; i < SIZES.length; i++) {
                ico.writeByte(SIZES[i] == 256 ? 0 : SIZES[i]);
                ico.writeByte(SIZES[i] == 256 ? 0 : SIZES[i]);
                ico.writeByte(0);
                ico.writeByte(0);
                littleShort(ico, 1);
                littleShort(ico, 32);
                littleInt(ico, images.get(i).length);
                littleInt(ico, offset);
                offset += images.get(i).length;
            }
            for (byte[] image : images) ico.write(image);
        }
    }

    private static BufferedImage render(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(size / 256.0, size / 256.0);
        g.setColor(Color.BLACK);
        g.fillRoundRect(1, 1, 254, 254, 58, 58);
        if (size > 32) {
            g.setColor(new Color(77, 77, 77));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(1, 1, 253, 253, 58, 58);
        }

        if (size > 48) {
            g.setColor(new Color(115, 115, 115));
            g.setStroke(new BasicStroke(2.1f));
            g.drawOval(67, 67, 122, 122);
            dot(g, 75, 98, 4.4);
            dot(g, 181, 98, 4.4);
            dot(g, 128, 189, 4.4);
        }

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(size <= 48 ? 8.0f : 6.5f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(128, 128, 128, 67);
        g.drawLine(128, 128, 75, 159);
        g.drawLine(128, 128, 181, 159);
        dot(g, 128, 67, size <= 48 ? 11 : 10.5);
        dot(g, 75, 159, size <= 48 ? 11 : 10.5);
        dot(g, 181, 159, size <= 48 ? 11 : 10.5);
        dot(g, 128, 128, size <= 48 ? 20 : 18.5);
        g.setColor(Color.BLACK);
        dot(g, 128, 128, size <= 48 ? 7 : 6.5);
        g.dispose();
        return image;
    }

    private static void dot(Graphics2D g, double x, double y, double radius) {
        g.fillOval((int) Math.round(x - radius), (int) Math.round(y - radius),
                (int) Math.round(radius * 2), (int) Math.round(radius * 2));
    }

    private static void littleShort(DataOutputStream output, int value) throws IOException {
        output.writeByte(value & 0xff);
        output.writeByte((value >>> 8) & 0xff);
    }

    private static void littleInt(DataOutputStream output, int value) throws IOException {
        littleShort(output, value & 0xffff);
        littleShort(output, value >>> 16);
    }
}
