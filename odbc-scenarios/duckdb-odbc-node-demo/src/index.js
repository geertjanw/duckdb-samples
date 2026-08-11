// Express entry point and HTTP routes - the Node analogue of DemoController
// plus DuckdbOdbcApplication.
//
// The ODBC connection is opened at startup and the process exits if MySQL is
// not reachable, matching the Java demo's fail-fast @PostConstruct behavior.

import express from 'express';

import { config } from './config.js';
import * as db from './duckdbConn.js';
import * as odbc from './odbcQueryService.js';
import * as benchmark from './benchmarkService.js';

const app = express();

// Wrap async handlers so a rejected promise becomes an error response instead
// of an unhandled rejection.
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res)).catch(next);

// Top customers, computed inside MySQL, fetched through ODBC.
app.get(
  '/customers/top',
  wrap(async (req, res) => {
    const limit = Number(req.query.limit ?? 10);
    res.json(await odbc.topCustomers(limit));
  })
);

// MySQL rows aggregated by DuckDB - the hybrid query demo.
app.get(
  '/customers/revenue-by-country',
  wrap(async (_req, res) => {
    res.json(await odbc.revenueByCountry());
  })
);

// Generate and load N rows into MySQL (default 100M).
app.post(
  '/benchmark/load',
  wrap(async (req, res) => {
    const rows = Number(req.query.rows ?? 100_000_000);
    res.json(await benchmark.generateAndLoadOrders(rows));
  })
);

// Benchmark the aggregation through the ODBC extension.
app.get(
  '/benchmark/odbc',
  wrap(async (_req, res) => {
    res.json(await benchmark.benchmarkOdbc());
  })
);

// Benchmark the same aggregation through the native MySQL scanner.
app.get(
  '/benchmark/scanner',
  wrap(async (_req, res) => {
    res.json(await benchmark.benchmarkMysqlScanner());
  })
);

// Benchmark the same aggregation on the local DuckDB table (no transfer).
app.get(
  '/benchmark/native',
  wrap(async (_req, res) => {
    res.json(await benchmark.benchmarkNative());
  })
);

// Error handler: surface the DuckDB/ODBC message as JSON.
app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: String(err?.message ?? err) });
});

async function main() {
  await db.connect();
  // Open the ODBC connection at startup; fail fast if MySQL isn't reachable.
  await odbc.init();
  const server = app.listen(config.port, () => {
    console.log(`Listening on http://localhost:${config.port}`);
  });
  server.on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
      console.error(
        `Port ${config.port} is already in use. Stop whatever is on it, ` +
          `or start on another port: PORT=8081 npm start`
      );
      process.exit(1);
    }
    throw err;
  });
}

main().catch((err) => {
  console.error('Startup failed:', err);
  process.exit(1);
});
