package demo;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Deliberately simple embedder: shrink the image to 8x8 and use the
 * red, green and blue value of every pixel, scaled to 0..1.
 * 8 * 8 * 3 = 192 numbers per image.
 */
public class PixelEmbedder implements Embedder {
    private static final int SIZE = 8;

    @Override
    public int dimensions() {
        return SIZE * SIZE * 3;
    }

    @Override
    public float[] embed(BufferedImage image) {
        BufferedImage small = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, SIZE, SIZE, null);
        g.dispose();

        float[] v = new float[dimensions()];
        int i = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int rgb = small.getRGB(x, y);
                v[i++] = ((rgb >> 16) & 0xFF) / 255f;
                v[i++] = ((rgb >> 8) & 0xFF) / 255f;
                v[i++] = (rgb & 0xFF) / 255f;
            }
        }
        return v;
    }
}
