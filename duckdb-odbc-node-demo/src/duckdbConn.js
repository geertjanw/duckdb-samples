// Single shared DuckDB connection, the Node analogue of DuckDbConfig.
//
// Why one connection? The ODBC extension stores its connection handle in a
// DuckDB *session variable* (SET VARIABLE conn = odbc_connect(...)), which is
// scoped to a single DuckDB connection. In the Java demo this forced a
// SingleConnectionDataSource instead of a pool. Here it is simpler: a
// DuckDBConnection object *is* one connection, so we create it once and share
// it. Each new connection to ':memory:' would be its own database, so sharing
// the handle is essential.
//
// A promise queue serializes statements: DuckDB runs them on one connection and
// SET VARIABLE state must not interleave across concurrent requests. This keeps
// the single-connection model honest, matching the Java and Python demos.

import { DuckDBInstance } from '@duckdb/node-api';
import { config } from './config.js';

let connection;
let tail = Promise.resolve(); // serializes access to the single connection

export async function connect() {
  const instance = await DuckDBInstance.create(config.duckdb.database);
  connection = await instance.connect();
}

// Serialize `fn` behind any in-flight work on the shared connection.
function enqueue(fn) {
  const run = tail.then(fn, fn);
  // Keep the chain alive even if a statement rejects.
  tail = run.then(
    () => undefined,
    () => undefined
  );
  return run;
}

// Run a query and return rows as plain JSON-safe objects (like
// JdbcTemplate.queryForList). getRowObjectsJson handles DuckDB types that
// aren't natively JSON-serializable: BIGINT -> string, DECIMAL -> string.
export function query(sql, params) {
  return enqueue(async () => {
    if (params && params.length) {
      const prepared = await connection.prepare(sql);
      bindAll(prepared, params);
      const reader = await prepared.runAndReadAll();
      return reader.getRowObjectsJson();
    }
    const reader = await connection.runAndReadAll(sql);
    return reader.getRowObjectsJson();
  });
}

// Run a statement that returns no rows we care about (like
// JdbcTemplate.execute/update).
export function execute(sql, params) {
  return enqueue(async () => {
    if (params && params.length) {
      const prepared = await connection.prepare(sql);
      bindAll(prepared, params);
      await prepared.run();
      return;
    }
    await connection.run(sql);
  });
}

// Bind positional parameters. DuckDB's Node API is typed per-bind; the demo
// only ever binds strings (ODBC connection string, credentials, SQL text).
function bindAll(prepared, params) {
  params.forEach((value, i) => prepared.bindVarchar(i + 1, String(value)));
}
