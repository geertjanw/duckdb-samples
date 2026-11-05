package demo;

import javax.imageio.ImageIO;
import javax.visrec.ml.classification.ClassificationException;
import javax.visrec.ml.classification.ImageClassifier;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A JSR 381 image classifier whose "model" is a DuckDB table of
 * labelled example images. Classifying = k-nearest-neighbour vote in SQL.
 */
public class DuckDbImageClassifier implements ImageClassifier<BufferedImage>, AutoCloseable {

    private final Connection conn;
    private final Embedder embedder;
    private final int k;

    public DuckDbImageClassifier(Path dbFile, Embedder embedder, int k) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:duckdb:" + dbFile.toAbsolutePath());
        this.embedder = embedder;
        this.k = k;
        try (Statement st = conn.createStatement()) {
            st.execute(Sql.CREATE_SEQUENCE);
            st.execute(Sql.sized(Sql.CREATE_TABLE, embedder.dimensions()));
        }
    }

    /** "Training": store one labelled example. */
    public void learn(String label, BufferedImage image, String source) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(Sql.sized(Sql.INSERT, embedder.dimensions()))) {
            ps.setString(1, label);
            ps.setString(2, source);
            ps.setString(3, toLiteral(embedder.embed(image)));
            ps.executeUpdate();
        }
    }

    // --- The three methods JSR 381's ImageClassifier requires ---

    @Override
    public Map<String, Float> classify(BufferedImage image) {
        try (PreparedStatement ps = conn.prepareStatement(Sql.sized(Sql.CLASSIFY, embedder.dimensions()))) {
            ps.setString(1, toLiteral(embedder.embed(image)));
            ps.setInt(2, k);
            Map<String, Float> result = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("label"), rs.getFloat("score"));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new ClassificationException("Query failed", e);
        }
    }

    @Override
    public Map<String, Float> classify(Path input) {
        try {
            return classify(read(ImageIO.read(input.toFile()), input.toString()));
        } catch (IOException e) {
            throw new ClassificationException("Cannot read " + input, e);
        }
    }

    @Override
    public Map<String, Float> classify(InputStream input) {
        try {
            return classify(read(ImageIO.read(input), "input stream"));
        } catch (IOException e) {
            throw new ClassificationException("Cannot read input stream", e);
        }
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }

    // --- helpers ---

    private static BufferedImage read(BufferedImage img, String what) {
        if (img == null) {
            throw new ClassificationException("Not a readable image: " + what);
        }
        return img;
    }

    /**
     * Passes the vector as text like "[0.1, 0.25, ...]" and lets DuckDB cast it.
     * Float.toString always uses a dot, so this is safe in any locale.
     */
    static String toLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(Float.toString(v[i]));
        }
        return sb.append(']').toString();
    }
}
