package demo;

import java.awt.image.BufferedImage;

/** Turns an image into a fixed-length vector. Swap this to improve accuracy. */
public interface Embedder {
    int dimensions();
    float[] embed(BufferedImage image);
}
