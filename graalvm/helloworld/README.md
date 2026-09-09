# helloworld

A smoke test: the smallest DuckDB JDBC program compiled to a GraalVM native
image. It opens an in-memory DuckDB connection, runs
`SELECT 42 AS answer, version() AS v`, and prints the row. If the binary runs
and prints the version, the native build has working DuckDB JDBC.

## Files

| File | Purpose |
|---|---|
| `Hello.java` | The whole program — connect, query, print |
| `META-INF/native-image/hello/reachability-metadata.json` | Reflection/JNI/resource metadata `native-image` needs for the DuckDB driver |
| `META-INF/native-image/hello/resource-config.json` | Resources bundled into the image |

There is no build tool here on purpose — just `javac` and `native-image` against
the DuckDB JDBC jar.

## Build and run

Get the DuckDB JDBC jar (for example from Maven Central,
`org.duckdb:duckdb_jdbc`) and put it next to `Hello.java`, then:

```bash
cd graalvm/helloworld

# compile against the DuckDB JDBC jar
javac -cp duckdb_jdbc-1.5.5.0.jar Hello.java

# build the native image (picks up META-INF/native-image from the classpath)
native-image -cp .:duckdb_jdbc-1.5.5.0.jar Hello hello

# run the standalone binary — no JVM needed
./hello
```

Expected output (version will vary):

```
42 v1.5.5
```

The jar, the `.class` file, and the `hello` binary are gitignored.
