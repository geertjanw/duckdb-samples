# graalvm

Compiling DuckDB JDBC applications to GraalVM native images — standalone
executables with no JVM to install and near-instant startup.

DuckDB's JDBC driver loads a native library and uses reflection, so a native
build needs reachability metadata telling `native-image` what to keep. Both
samples here ship that metadata under `META-INF/native-image/`, so the build
works without extra flags for it.

| Project | What it shows |
|---|---|
| [`helloworld`](helloworld) | The smallest case: one class, `SELECT 42, version()`, compiled to a native binary. Plain `javac` + `native-image`, no build tool. |
| [`recon`](recon) | A real CLI: an order-to-cash reconciliation tool that queries JSONL, CSV, and Parquet exports with DuckDB. Maven + the GraalVM `native-maven-plugin`. |

Both need a GraalVM JDK with `native-image` installed:

```bash
java -version          # should report GraalVM
native-image --version
```

See each project's README for build and run steps.
