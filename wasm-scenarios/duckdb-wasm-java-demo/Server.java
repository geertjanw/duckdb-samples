import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

public class Server {

    public static void main(String[] args) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Data endpoint: Java produces the data
        server.createContext("/api/sales.csv", ex -> {
            var rnd = new Random(42);
            String[] regions = {"North", "South", "East", "West"};
            String[] products = {"Coffee", "Tea", "Juice"};

            var sb = new StringBuilder("region,product,amount\n");
            for (int i = 0; i < 1000; i++) {
                sb.append(regions[rnd.nextInt(regions.length)]).append(',')
                  .append(products[rnd.nextInt(products.length)]).append(',')
                  .append(String.format(Locale.ROOT, "%.2f", 5 + rnd.nextDouble() * 95))
                  .append('\n');
            }
            send(ex, "text/csv", sb.toString().getBytes(StandardCharsets.UTF_8));
        });

        // Static page: the browser does the analytics
        server.createContext("/", ex ->
            send(ex, "text/html; charset=utf-8", Files.readAllBytes(Path.of("index.html"))));

        server.start();
        System.out.println("Open http://localhost:8080");
    }

    private static void send(HttpExchange ex, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(200, body.length);
        try (var os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
