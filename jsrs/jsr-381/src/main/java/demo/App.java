package demo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Usage:
 *   train <folder>     folder contains one subfolder per label, each with images
 *   classify <image>   prints label scores, best first
 */
public class App {
    private static final Path DB = Path.of("model.duckdb");

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: train <folder> | classify <image>");
            return;
        }
        try (var classifier = new DuckDbImageClassifier(DB, new PixelEmbedder(), 5)) {
            switch (args[0]) {
                case "train" -> train(classifier, Path.of(args[1]));
                case "classify" -> print(classifier.classify(Path.of(args[1])));
                default -> System.out.println("Unknown command: " + args[0]);
            }
        }
    }

    private static void train(DuckDbImageClassifier classifier, Path root) throws Exception {
        int count = 0;
        try (Stream<Path> labels = Files.list(root)) {
            for (Path labelDir : labels.filter(Files::isDirectory).sorted().toList()) {
                String label = labelDir.getFileName().toString();
                for (Path file : images(labelDir)) {
                    BufferedImage img = ImageIO.read(file.toFile());
                    if (img == null) continue; // skip non-images
                    classifier.learn(label, img, file.toString());
                    count++;
                }
            }
        }
        System.out.println("Stored " + count + " examples in " + DB);
    }

    private static List<Path> images(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static void print(Map<String, Float> scores) {
        scores.forEach((label, score) -> System.out.printf("%-12s %.3f%n", label, score));
    }
}
