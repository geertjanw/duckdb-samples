// Uses the DuckDB ODBC extension (a.k.a. odbc_scanner) to query MySQL.
//
// Note the direction of the ODBC arrow:
//  - DuckDB's ODBC *client/driver* lets other apps connect INTO DuckDB.
//  - The ODBC *extension* (used here) lets DuckDB connect OUT to other
//    databases through their ODBC drivers.

import * as db from './duckdbConn.js';
import { config } from './config.js';

// Install/load the odbc extension and open the ODBC connection once. The handle
// is stashed in a DuckDB session variable, exactly as in the Java demo. Because
// we run everything on one shared connection, the variable stays visible across
// every request.
export async function init() {
  console.log('Installing and loading DuckDB odbc extension...');
  await db.execute('INSTALL odbc');
  await db.execute('LOAD odbc');

  // Open the ODBC connection once and stash the handle in a session variable.
  await db.execute('SET VARIABLE conn = odbc_connect(?, ?, ?)', [
    config.mysql.odbcConnectionString,
    config.mysql.username,
    config.mysql.password,
  ]);
  console.log('Connected to MySQL via ODBC.');
}

// Run an arbitrary (read-only) SQL statement on MySQL through ODBC.
export function queryMysql(mysqlSql) {
  return db.query("SELECT * FROM odbc_query(getvariable('conn'), ?)", [mysqlSql]);
}

// Simple demo query: top customers by revenue, computed inside MySQL.
export function topCustomers(limit) {
  return queryMysql(
    `SELECT id, name, country, revenue
     FROM customers
     ORDER BY revenue DESC
     LIMIT ${Number(limit)}`
  );
}

// DuckDB's real superpower: pull rows from MySQL over ODBC and aggregate them
// in DuckDB, in one SQL statement.
export function revenueByCountry() {
  return db.query(
    `SELECT country, count(*) AS customers, sum(revenue) AS total_revenue
     FROM odbc_query(getvariable('conn'),
          'SELECT country, revenue FROM customers')
     GROUP BY country
     ORDER BY total_revenue DESC`
  );
}
