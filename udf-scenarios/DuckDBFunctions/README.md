# User-Defined Functions in the DuckDB Java Driver — Samples

Companion code for defining **user-defined functions (UDFs)** in the DuckDB JDBC driver. The driver can register both **scalar functions** (one result per row) and **table functions** (a set of rows), all written in Java and called from SQL. Each sample is a self-contained `main` that registers one function and calls it.

| Sample | File | What it shows |
|---|---|---|
| 1 | `S01_ScalarVarArgs` | A scalar function with a **variable** number of arguments (`withVarArgs` / `withVarArgsFunction`) |
| 2 | `S02_ScalarFixedArgs` | A scalar function with a **fixed** number of arguments (`withParameters` / `withFunction`), including SQL `NULL` handling |
| 3 | `S03_VectorizedScalar` | A **vectorized** scalar function that processes a whole batch of rows per call (`withVectorizedFunction`) |
| 4 | `S04_TableFunction` | A basic **table function** with the `bind` / `init` / `apply` callbacks |
| 5 | `S05_TableFunctionResource` | A table function that holds an **open resource** (a file reader) and releases it when the scan ends |
| 6 | `S06_ParallelTableFunction` | A table function that runs on **multiple threads** (`setMaxThreads` + `preserve_insertion_order = false`) |

## Requirements

- Java 17+
- Maven 3.x
- Internet access to Maven Central on first build (the `duckdb_jdbc` jar embeds the native DuckDB library — nothing else to install)

## Run

```bash
mvn -q compile
mvn -q exec:java -Dexec.mainClass=org.example.duckdb.S01_ScalarVarArgs
mvn -q exec:java -Dexec.mainClass=org.example.duckdb.S02_ScalarFixedArgs
mvn -q exec:java -Dexec.mainClass=org.example.duckdb.S03_VectorizedScalar
mvn -q exec:java -Dexec.mainClass=org.example.duckdb.S04_TableFunction
mvn -q exec:java -Dexec.mainClass=org.example.duckdb.S05_TableFunctionResource
mvn -q exec:java -Dexec.mainClass=org.example.duckdb.S06_ParallelTableFunction
```

## How UDFs are built

- **Scalar functions** — `DuckDBFunctions.scalarFunction()`, then `.withName(...)`, the parameter/return types, an implementation, and `.register(conn)`. For a fixed arity use `withParameter`/`withParameters` + `withFunction` (a `Supplier`, `Function`, or `BiFunction`); for a variadic list use `withVarArgs` + `withVarArgsFunction` (an `Object[]` callback). To process a batch at once, use `withVectorizedFunction`, whose callback reads a `DuckDBDataChunkReader` and writes a `DuckDBWritableVector` (up to 2048 rows, same row index in and out). Object-argument callbacks receive `null` for SQL `NULL`.
- **Table functions** — `DuckDBFunctions.tableFunction()` + a `DuckDBTableFunction` with three callbacks: `bind` (declares output columns with `addResultColumn`, reads call parameters, returns bind data), `init` (returns the global state), and `apply` (writes rows through a `DuckDBDataChunkWriter` and returns the count; the engine calls it until it returns 0). Its three type parameters are, in order, the bind data, global init data, and local init data.

## Type mapping

When a UDF declares a parameter or return type with a Java `Class`, the driver maps it to a DuckDB type:

| Java type | DuckDB type |
|---|---|
| `int` | `INTEGER` |
| `long` | `BIGINT` |
| `float` | `FLOAT` |
| `double` | `DOUBLE` |
| `String` | `VARCHAR` |
| `BigDecimal` | `DECIMAL` |
| `BigInteger` | `HUGEINT` |
| `LocalDate`, `java.sql.Date` | `DATE` |
| `LocalDateTime`, `java.sql.Timestamp`, `java.util.Date` | `TIMESTAMP` |

For types without a direct Java mapping, declare the type explicitly with `DuckDBColumnType` or `DuckDBLogicalType` — e.g. `DuckDBColumnType.UHUGEINT`, or `DuckDBLogicalType.decimal(width, scale)` for an explicit `DECIMAL` precision/scale. Composite types (`LIST`, `STRUCT`) are not yet supported in UDFs.

## Notes

- Driver version is pinned in `pom.xml` (`1.5.5.1`, latest at the time of writing — Aug 2026). Cross-check method names against your driver version if it differs.
- **Deterministic resource cleanup:** the docs describe a `DuckDBTableFunctionState` interface whose `close()` DuckDB calls deterministically when a scan finishes (also after a `LIMIT`, error, cancellation, or early JDBC close). That interface is **not present in 1.5.5.1**, so `S05_TableFunctionResource` closes its reader itself on end-of-file. Once the interface ships, have the state implement it and move the `close()` there — that also covers the `LIMIT`/early-exit cases this sample cannot.
- `DuckDBLogicalType` is a native resource — create it in a try-with-resources block, as the scalar samples do.
- Indices: statement parameters are 1-based (JDBC tradition); chunk/vector columns and rows are 0-based (matching the C API).
- Everything runs in-memory (`jdbc:duckdb:`); sample 5 writes one small temp file (deleted on exit).

## Further reading

- Java client docs: https://duckdb.org/docs/current/clients/java
- Related: the `chunked-scenarios` samples cover the read side (`DuckDBDataChunkReader`) that the vectorized scalar function mirrors.
