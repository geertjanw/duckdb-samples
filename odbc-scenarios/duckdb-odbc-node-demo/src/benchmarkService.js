// Benchmark:
//  1. Generate a large orders table and load it into MySQL/MariaDB.
//  2. Benchmark the same aggregation through
//     (a) the ODBC extension, (b) the native MySQL scanner extension, and
//     (c) a local DuckDB table as the no-transfer baseline.
//
// Uses the DuckDB core `odbc_scanner` extension API:
//  - odbc_query(conn, sql)  -- table function; runs any SQL (incl. DDL) in the remote DB
//  - odbc_copy(conn, ...)   -- bulk-loads rows into the remote DB over ODBC,
//                              batching inserts (up to 2048 rows per SQLExecute)
// Note: there is no odbc_exec in this extension; DDL also goes through odbc_query.
//
// MariaDB/MySQL specifics encountered along the way:
//  - odbc_copy's create_table=true fails ("column type not recognized:
//    DUCKDB_TYPE_BIGINT"): the automatic type mapping is incomplete for
//    MySQL/MariaDB (Tier 2 support). We create the table explicitly instead,
//    which also lets us keep the PRIMARY KEY.
//  - odbc_copy quotes column names with double quotes by default, which MariaDB
//    rejects in its default sql_mode (it expects backticks), hence column_quotes = '`'.

import os from 'node:os';
import path from 'node:path';

import * as db from './duckdbConn.js';
import { config } from './config.js';

let scannerAttached = false;

// Generate `rows` orders into a local DuckDB table (which doubles as the
// baseline for the native benchmark), stage them as CSV, and bulk-load them
// into MySQL with odbc_copy. Row-by-row INSERTs over ODBC would take forever at
// this scale; odbc_copy batches up to 2048 rows per round trip and wraps the
// whole load in a remote transaction.
//
// The CSV detour exists because odbc_copy runs its source query in a separate
// DuckDB instance and cannot see orders_local directly. The CSV column order
// must match the remote table column order.
export async function generateAndLoadOrders(rows) {
  const report = {};
  const csvPath = path.join(os.tmpdir(), 'orders.csv');

  // 1. Generate into a local table: the native benchmark scans this.
  let t0 = now();
  await db.execute(
    `CREATE OR REPLACE TABLE orders_local AS
     SELECT range AS id,
            (range % 1000)::INTEGER AS customer_id,
            round(random() * 100, 2) AS amount
     FROM range(${Number(rows)})`
  );
  report.generate_seconds = secondsSince(t0);

  // 2. Stage as CSV for odbc_copy.
  t0 = now();
  await db.execute(`COPY orders_local TO '${csvPath}' (FORMAT CSV, HEADER true)`);
  report.stage_csv_seconds = secondsSince(t0);

  // 3. Remote DDL goes through odbc_query (a table function, hence FROM ...).
  //    Explicit CREATE TABLE because create_table=true fails for MySQL/MariaDB
  //    ("column type not recognized: DUCKDB_TYPE_BIGINT").
  await db.execute(
    "FROM odbc_query(getvariable('conn'), 'DROP TABLE IF EXISTS orders')"
  );
  await db.execute(
    `FROM odbc_query(getvariable('conn'),
        'CREATE TABLE orders (
             id BIGINT PRIMARY KEY,
             customer_id INT,
             amount DOUBLE)')`
  );

  // 4. Bulk-load over ODBC. column_quotes: MariaDB's default sql_mode rejects
  //    double-quoted identifiers, so quote with backticks.
  t0 = now();
  await db.execute(
    `FROM odbc_copy(getvariable('conn'),
        source_file = '${csvPath}',
        dest_table = 'orders',
        batch_size = 2048,
        column_quotes = '\`')`
  );
  report.mysql_load_seconds = secondsSince(t0);
  report.rows = rows;
  console.log(`Loaded ${rows} rows into MySQL:`, report);
  return report;
}

// Aggregation pushed through the ODBC extension (row-by-row transfer).
export async function benchmarkOdbc() {
  const t0 = now();
  const result = await db.query(
    `SELECT customer_id, sum(amount) AS total
     FROM odbc_query(getvariable('conn'),
          'SELECT customer_id, amount FROM orders')
     GROUP BY customer_id
     ORDER BY total DESC
     LIMIT 5`
  );
  return report('odbc_extension', secondsSince(t0), result);
}

// Same aggregation through the native MySQL scanner extension.
export async function benchmarkMysqlScanner() {
  await attachMysqlScanner();
  const t0 = now();
  const result = await db.query(
    `SELECT customer_id, sum(amount) AS total
     FROM mysqldb.orders
     GROUP BY customer_id
     ORDER BY total DESC
     LIMIT 5`
  );
  return report('mysql_scanner', secondsSince(t0), result);
}

// Same aggregation on the local DuckDB table: the no-transfer baseline. Same
// rows as MySQL holds (loaded from the same generation), so the top-5 output
// matches the other two paths for a given load.
export async function benchmarkNative() {
  const t0 = now();
  const result = await db.query(
    `SELECT customer_id, sum(amount) AS total
     FROM orders_local
     GROUP BY customer_id
     ORDER BY total DESC
     LIMIT 5`
  );
  return report('native_duckdb', secondsSince(t0), result);
}

// The scanner ATTACH is one-time per session; attaching twice with the same
// alias fails, so guard it. Lazy so the app starts fine when only the ODBC
// endpoints are used. READ_ONLY lets DuckDB skip transaction bookkeeping.
async function attachMysqlScanner() {
  if (scannerAttached) return;
  await db.execute('INSTALL mysql');
  await db.execute('LOAD mysql');
  await db.execute(
    `ATTACH '${config.mysql.scannerConnectionString}' AS mysqldb (TYPE mysql, READ_ONLY)`
  );
  scannerAttached = true;
  console.log('MySQL scanner attached.');
}

function now() {
  return process.hrtime.bigint();
}

function secondsSince(start) {
  return Math.round(Number(process.hrtime.bigint() - start) / 1e7) / 100;
}

function report(pathName, seconds, sample) {
  return { path: pathName, seconds, top5: sample };
}
