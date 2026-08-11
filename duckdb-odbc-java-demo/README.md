# DuckDB ODBC Extension × Spring Boot Demo (Java)

> Java/Spring Boot edition. For the same demo built with FastAPI, see
> [`duckdb-odbc-python-demo`](../duckdb-odbc-python-demo); for Node.js/Express,
> see [`duckdb-odbc-node-demo`](../duckdb-odbc-node-demo).

Queries MySQL through the **DuckDB ODBC extension**, with DuckDB embedded
in a Spring Boot application:

```
Spring Boot → JDBC → DuckDB (in-process) → odbc extension → unixODBC → MariaDB ODBC driver → MySQL
```

Every hop in that chain exists for a reason:

- **Spring Boot → JDBC** — Java's standard database API; `JdbcTemplate`
  needs *some* JDBC driver, and DuckDB ships one.
- **DuckDB (in-process)** — DuckDB is an embedded database: it runs inside
  the JVM as a library, so there is no DuckDB server to deploy or manage.
- **odbc extension** — DuckDB core has no MySQL client built in; the
  extension adds the ability to reach out to external databases.
- **unixODBC** — the extension is written against the ODBC *API*, not
  against any particular driver. A driver manager is what resolves
  `Driver={MariaDB}` to an actual `.so` and dispatches the calls; on
  Linux/macOS that manager is unixODBC (Windows has one built in).
- **MariaDB ODBC driver** — the piece that actually implements the MySQL
  wire protocol. unixODBC only routes calls; without a driver there is
  nothing to route them *to*.

## ODBC extension vs. ODBC client — don't mix them up

| | Direction | Who connects to whom |
|---|---|---|
| **ODBC client (driver)** | inbound | External tools (Excel, Power BI) connect *into* DuckDB |
| **ODBC extension** (`odbc`) | outbound | DuckDB connects *out* to other databases (used here) |

Both exist because ODBC has two sides: a data source that *implements* the
API, and a consumer that *calls* it. DuckDB can play either role; this demo
uses the consumer side only.

## Prerequisites

Everything that must be in place before `mvn spring-boot:run` works, and
why:

1. **Java 17+ and Maven** — the app uses Java text blocks (15+) and Spring
   Boot 3, which requires 17. DuckDB itself needs no separate install: the
   `duckdb_jdbc` Maven dependency bundles the native library and loads it
   at runtime.

2. **Docker + Docker Compose** — provides the MySQL side of the demo
   (a MariaDB container seeded with the small `customers` dataset) without
   you having to install and configure a database server on the host.

3. **unixODBC driver manager:**

   ```bash
   sudo apt install unixodbc
   ```

   *Why:* the DuckDB ODBC extension never loads database drivers itself —
   it calls the standard ODBC API and relies on a driver manager to find
   the right driver and forward the calls. Without unixODBC, `odbc_connect`
   has no way to resolve a connection string at all.

   *Additional notes:*
   - Only Linux and macOS need this step — Windows ships its own driver
     manager (the built-in ODBC Data Source Administrator).
   - On other platforms: Fedora/RHEL `sudo dnf install unixODBC`;
     macOS `brew install unixodbc`.
   - The package also installs two diagnostic tools you'll want:
     `odbcinst -j` prints which config files unixODBC actually reads
     (useful when an edit seems to have no effect — you may be editing the
     wrong file), and `isql <DSN>` lets you test a connection *outside*
     DuckDB and the JVM, which cleanly separates "ODBC is broken" from
     "the app is broken".
   - Config lookup can be redirected with environment variables:
     `ODBCSYSINI` points to the directory holding `odbcinst.ini`/`odbc.ini`
     (handy in containers where you can't write to `/etc`), and unixODBC
     also honors per-user `~/.odbcinst.ini` / `~/.odbc.ini`.

4. **A MySQL-compatible ODBC driver:**

   ```bash
   sudo apt install odbc-mariadb
   ```

   *Why:* this is the component that actually implements the ODBC API for
   MySQL/MariaDB — it opens the TCP connection, handles the wire protocol,
   and translates results into ODBC's C types. The MariaDB driver is used
   because it's protocol-compatible with MySQL and packaged in Debian/
   Ubuntu; MySQL's own Connector/ODBC would work too.

   *Additional notes:*
   - On other platforms: Fedora/RHEL `sudo dnf install
     mariadb-connector-odbc`; macOS `brew install mariadb-connector-odbc`
     (Homebrew builds it against its own unixODBC, so install that first).
   - If you switch to MySQL's Connector/ODBC instead, everything downstream
     stays the same — you only register it under a different section name
     and reference that name in the connection string
     (e.g. `Driver={MySQL ODBC 9.x Unicode Driver}`).
   - The driver's architecture must match the *process* loading it: a
     64-bit JVM (hence 64-bit DuckDB) can only load a 64-bit driver `.so`.
     Distro packages get this right automatically; it mainly bites when a
     driver was installed manually from a vendor tarball.
   - Installing the package does **not** make the driver usable yet — the
     driver manager still has no idea it exists. That's step 5.

5. **Driver registration** in `/etc/odbcinst.ini`:

   ```ini
   [MariaDB]
   Driver=/usr/lib/x86_64-linux-gnu/odbc/libmaodbc.so
   ```

   *Why:* installing the driver only puts a `.so` on disk. unixODBC
   resolves the `Driver={MariaDB}` token in the connection string by
   looking up a section with that exact name in `odbcinst.ini` — no entry,
   no connection (you get error `IM002`). The `.so` path differs per
   distro; find it with `dpkg -L odbc-mariadb | grep maodbc`. Verify the
   registration from any DuckDB shell with `FROM odbc_list_drivers();`.

   *Additional notes:*
   - On Debian/Ubuntu, `odbc-mariadb` may already register itself during
     `apt install` (the package hooks into unixODBC's registration
     mechanism). Check with `odbcinst -q -d` before adding the entry by
     hand — if it's there under a name like `[MariaDB Unicode]`, either use
     that name in the connection string or add your own `[MariaDB]` alias.
   - Registration can also be done with the CLI instead of editing the file:
     `sudo odbcinst -i -d -f driver-template.ini` (where the template
     contains the same two lines). Same result, less risk of typos.
   - `odbcinst.ini` (drivers) is not `odbc.ini` (DSNs). This demo uses a
     **DSN-less** connection string — driver name plus `Server`, `Port`,
     `Database` inline — so only `odbcinst.ini` is needed. Alternatively
     you could define a named DSN in `odbc.ini` referencing the driver and
     connect with just `DSN=mydsn`; useful if you want connection details
     out of `application.yml`, at the cost of another host-specific file.
   - Two DuckDB-side sanity checks: `FROM odbc_list_drivers();` shows what
     the driver manager can see, and `FROM odbc_list_data_sources();` lists
     any DSNs. If a driver appears in `odbcinst -q -d` but not in
     `odbc_list_drivers()`, DuckDB's unixODBC is reading a different config
     directory — compare with `odbcinst -j` / `ODBCSYSINI`.
   - Last-resort debugging: enable driver-manager tracing by adding
     `[ODBC]` / `Trace=Yes` / `TraceFile=/tmp/odbc.trace` to `odbcinst.ini`
     — the trace shows every ODBC call and which driver handled it. Turn it
     off afterwards; it's extremely verbose and slows everything down.

6. **DuckDB extensions** (`odbc`, and `mysql` for the benchmark) — nothing
   for you to do, but worth knowing: the app runs `INSTALL odbc; LOAD
   odbc;` at startup because extensions are not compiled into DuckDB —
   `INSTALL` downloads them once from the DuckDB extension repository (so
   the first run needs network access) and caches them in `~/.duckdb`,
   while `LOAD` links the extension into the current session and is
   required on every startup, since DuckDB never auto-loads this extension.

## Configuration

All connection details live under custom `app.*` properties in
`src/main/resources/application.yml` — the demo does **not** use Spring's
`spring.datasource` auto-configuration (see the `SET VARIABLE` gotcha for
why):

```yaml
app:
  duckdb:
    # In-memory DuckDB. Use e.g. jdbc:duckdb:/tmp/demo.duckdb for a persistent file.
    url: "jdbc:duckdb:"
  mysql:
    # ODBC connection string used by the DuckDB odbc extension.
    # {MariaDB} must match the driver name registered in /etc/odbcinst.ini
    odbc-connection-string: "Driver={MariaDB};Server=127.0.0.1;Port=3306;Database=demo"
    # Native connection string used by the DuckDB mysql scanner extension.
    scanner-connection-string: "host=127.0.0.1 port=3306 user=app_user password=app_password database=demo"
    username: app_user
    password: app_password
```

`DuckDbConfig` builds a `JdbcTemplate` from `app.duckdb.url` on a
**single shared DuckDB connection** (a `SingleConnectionDataSource`), and
the ODBC connection is opened at startup from
`app.mysql.odbc-connection-string` plus the credentials.

## Run it

```bash
# 1. Start MySQL (MariaDB) with a small demo dataset.
#    Must be first: the app opens its ODBC connection at startup and
#    fails fast if the database isn't reachable.
docker compose up -d

# 2. Build: compiles and runs tests, and catches problems before the
#    app tries to open its ODBC connection.
mvn clean package

# 3. Start the app
mvn spring-boot:run
```

## Try it

```bash
# Query computed inside MySQL, fetched via ODBC.
# Shows plain pass-through: the SQL string inside odbc_query() is executed
# by MySQL; DuckDB only relays the (small) result.
curl 'localhost:8080/customers/top?limit=5'

# Hybrid: MySQL rows streamed over ODBC, aggregated by DuckDB.
# Shows the actual point of the extension: remote rows become regular
# DuckDB rows you can aggregate or join against local data.
curl 'localhost:8080/customers/revenue-by-country'
```

## 100M-row benchmark

```bash
# Generate rows in DuckDB and bulk-load into MySQL.
# Start smaller (e.g. 10M) to test; 100M needs a few GB of disk and patience.
# Run this in the SAME session before /benchmark/native: orders_local is an
# in-memory table and is gone after a restart (see Gotchas).
curl -X POST 'localhost:8080/benchmark/load?rows=10000000'

# Aggregate through the ODBC extension (row-by-row transfer)
curl 'localhost:8080/benchmark/odbc'

# Same aggregation through DuckDB's native mysql scanner extension
curl 'localhost:8080/benchmark/scanner'

# Same aggregation on the local DuckDB table (no-transfer baseline)
curl 'localhost:8080/benchmark/native'

# Iterate
for p in odbc scanner native; do                           
  echo "== $p =="
  for i in 1 2 3 4 5 6; do curl -s "localhost:8080/benchmark/$p"; echo; done
done
```

*Why three paths:* they answer the same question through different
transports. The ODBC path is the universal, works-with-anything adapter;
the `mysql` scanner uses the MySQL wire protocol natively; the local
table has no transfer at all. Running the identical aggregation over all
three isolates the cost of the transport itself — the load generates the
rows into `orders_local` first and MySQL is filled from the same data,
so all three return the same top-5.

The load endpoint reports per-phase timings (`generate_seconds`,
`stage_csv_seconds`, `mysql_load_seconds`), so you can tell data
generation apart from ODBC transfer.

Expected pattern: the native `mysql` scanner is several times faster than the
ODBC path, because it reads and converts batches over the MySQL wire
protocol, while ODBC fetches and converts values cell by cell. Both are
bounded by transfer speed, not by DuckDB's aggregation.

## Gotchas

Things that cost debugging time, so you don't have to:

- **`LOAD odbc` is not optional.** `INSTALL` happens once (and DuckDB can
  even install known extensions implicitly), but the extension is never
  auto-*loaded*; without an explicit `LOAD odbc` in each session, every
  `odbc_*` function fails with "function does not exist".

- **The driver name must match exactly.** `Driver={MariaDB}` in the
  connection string is looked up against the `[MariaDB]` section name in
  `/etc/odbcinst.ini`. A mismatch yields the unhelpful
  `Data source name not found and no default driver specified (IM002)`.

- **`SET VARIABLE` is per-DuckDB-connection.** The ODBC handle stored via
  `SET VARIABLE conn = odbc_connect(...)` exists only on the connection
  that set it. A pooled data source hands out different connections per
  request, so `getvariable('conn')` intermittently returns NULL. Worse,
  with `jdbc:duckdb:` each new connection is its *own* in-memory database —
  pooled connections wouldn't even share tables or loaded extensions.
  That's why this demo skips Spring's auto-configured pool entirely:
  `DuckDbConfig` builds the `JdbcTemplate` on a **single shared DuckDB
  connection** (`SingleConnectionDataSource` from `app.duckdb.url`). For
  production, open the ODBC connection per statement instead (pass a
  connection string directly to `odbc_query`/`odbc_copy` with
  `close_connection = true` — stateless, therefore pool-safe).

- **There is no `odbc_exec`.** DDL against the remote DB also goes through
  the `odbc_query` **table function** — which means it must appear in a
  `FROM` clause: `FROM odbc_query(getvariable('conn'), 'DROP TABLE IF
  EXISTS orders')`. A bare `SELECT odbc_query(...)` won't parse.
  (An `odbc_exec` exists in the unrelated *nanodbc* community extension —
  easy to confuse when googling.)

- **`odbc_copy` can't see your in-memory tables.** Its source query runs in
  a separate DuckDB instance, so `source_query = 'FROM my_table'` fails for
  tables of the running app. That's why `BenchmarkService` stages the data
  as a CSV first and passes `source_file`.

- **`create_table = true` does not work against MySQL/MariaDB.** It fails
  with `column type not recognized: DUCKDB_TYPE_BIGINT` — the automatic
  type mapping is incomplete for MySQL/MariaDB (Tier 2 support). The
  workaround, used here, is to create the remote table explicitly through
  `odbc_query` before the copy — which also lets you define a PRIMARY
  KEY, something CTAS never gives you. The CSV column order must then
  match the table column order.

- **MariaDB rejects `odbc_copy`'s default identifier quoting.** The
  generated `INSERT` double-quotes column names, which MariaDB's default
  `sql_mode` refuses (it expects backticks). Pass
  ``column_quotes = '`'`` to `odbc_copy`. (Alternatively, set
  `sql_mode=ANSI_QUOTES` on the server — but changing the client is less
  invasive than changing the database.)

- **`batch_size` only accepts powers of two, max 2048.** The default is
  only 16 rows per `SQLExecute`; the benchmark uses 2048 because at 100M
  rows the per-round-trip overhead dominates. Arbitrary values like 1000
  are rejected.

- **Disk space for the staged CSV.** The generated `orders.csv` lands in
  `java.io.tmpdir` and reaches several GB at 100M rows. Make sure `/tmp`
  isn't a small tmpfs.

- **ODBC reads are slow by design.** `odbc_query` is single-threaded, makes
  multiple ODBC API calls per row, and converts every string from UCS-2 to
  UTF-8. Select only the columns you need — `SELECT *` measurably inflates
  transfer time.

- **The scanner ATTACH is one-time per session.** Attaching twice with the
  same alias fails, hence the lazy, guarded
  `ATTACH '...' AS mysqldb (TYPE mysql, READ_ONLY)` in `BenchmarkService`.
  `READ_ONLY` because the benchmark only reads and it lets DuckDB skip
  transaction bookkeeping on the attached database.

- **`orders_local` does not survive a restart, but MySQL's `orders` does.**
  `orders_local` is an in-memory DuckDB table — it exists only for the life of
  the running process — while MySQL's `orders` lives in the Docker container
  and persists. So after you restart the app without re-running the load,
  `/benchmark/odbc` and `/benchmark/scanner` still work (they read MySQL) but
  `/benchmark/native` fails with `Catalog Error: Table with name orders_local
  does not exist`. The fix is to run `POST /benchmark/load` again in the
  current session before hitting `/benchmark/native`; the load regenerates
  `orders_local` and refills MySQL from the same data. (Point
  `app.duckdb.url` at a file instead of in-memory to make it persist.)

## Implementation notes

- **Single DuckDB connection** (`DuckDbConfig`): the ODBC handle lives in a
  DuckDB session variable (`SET VARIABLE conn = odbc_connect(...)`), which is
  scoped to one connection — see Gotchas. The connection string and
  credentials come from `app.mysql.*` in `application.yml`.
- **Bulk load** (`BenchmarkService`): DuckDB generates the orders into a
  local table (`orders_local`, which the native benchmark scans), stages
  it as CSV (`COPY orders_local TO ... (FORMAT CSV, HEADER true)`), creates
  the remote table explicitly through `odbc_query` (see Gotchas), then
  `odbc_copy` bulk-loads the CSV with `batch_size = 2048` — the maximum
  rows per `SQLExecute` round trip — and ``column_quotes = '`'``. The whole
  load runs inside a remote transaction (`odbc_copy`'s default), so a
  failed load rolls back instead of leaving a half-filled table.
  Row-by-row INSERTs over ODBC would be impractically slow at this scale.
- DDL goes through the `odbc_query` table function (hence
  `FROM odbc_query(...)`), e.g. the `DROP TABLE IF EXISTS orders` before
  each load — dropping first makes the load endpoint idempotent.
- The `mysql` scanner is attached lazily (`INSTALL mysql; LOAD mysql;`) with
  `ATTACH '...' AS mysqldb (TYPE mysql, READ_ONLY)`, using
  `app.mysql.scanner-connection-string` — lazily so the app starts fine
  even when only the ODBC endpoints are used.