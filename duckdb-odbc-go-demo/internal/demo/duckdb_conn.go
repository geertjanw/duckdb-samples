// Single shared DuckDB connection, the Go analogue of DuckDbConfig.
//
// Why one connection? The ODBC extension stores its connection handle in a
// DuckDB *session variable* (SET VARIABLE conn = odbc_connect(...)), which is
// scoped to a single DuckDB connection. In the Java demo this forced a
// SingleConnectionDataSource instead of a pool. Here we use database/sql but
// pin the pool to exactly one underlying connection, so every statement runs
// on the same DuckDB session and SET VARIABLE state survives across requests.
// With ':memory:', each new connection would be its own database anyway, so
// sharing the one connection is essential, not just an optimization.
//
// database/sql already serializes callers onto a single connection when
// MaxOpenConns is 1, which keeps the single-connection model honest and matches
// the Java, Python, and Node demos.
package demo

import (
	"context"
	"database/sql"
	"fmt"

	duckdb "github.com/duckdb/duckdb-go/v2" // also registers the "duckdb" driver
)

// DuckDB wraps the single shared *sql.DB.
type DuckDB struct {
	db *sql.DB
}

// OpenDuckDB opens the in-memory (or file-backed) DuckDB and pins the pool to a
// single connection. Empty string opens an in-memory database.
func OpenDuckDB(database string) (*DuckDB, error) {
	// The duckdb driver treats an empty DSN as in-memory; translate our
	// ":memory:" convention (shared with the other demos) accordingly.
	dsn := database
	if dsn == ":memory:" {
		dsn = ""
	}

	db, err := sql.Open("duckdb", dsn)
	if err != nil {
		return nil, fmt.Errorf("open duckdb: %w", err)
	}

	// Pin to one physical connection so the ODBC session variable, loaded
	// extensions, and in-memory tables all persist across requests. Without
	// this, database/sql could open a second connection (fresh :memory: db) or
	// silently drop and recreate the idle one, losing all session state.
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)
	db.SetConnMaxLifetime(0) // never expire
	db.SetConnMaxIdleTime(0) // never close while idle

	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("ping duckdb: %w", err)
	}
	return &DuckDB{db: db}, nil
}

// Close releases the underlying connection.
func (d *DuckDB) Close() error { return d.db.Close() }

// Exec runs a statement that returns no rows we care about (like
// JdbcTemplate.execute/update): INSTALL/LOAD, SET VARIABLE, ATTACH, COPY.
func (d *DuckDB) Exec(ctx context.Context, sql string, args ...any) error {
	_, err := d.db.ExecContext(ctx, sql, args...)
	return err
}

// Query runs a query and returns rows as plain JSON-safe maps (like
// JdbcTemplate.queryForList). Column order is not preserved (JSON objects are
// unordered), matching the other demos' object output.
func (d *DuckDB) Query(ctx context.Context, sql string, args ...any) ([]map[string]any, error) {
	rows, err := d.db.QueryContext(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	cols, err := rows.Columns()
	if err != nil {
		return nil, err
	}

	var out []map[string]any
	for rows.Next() {
		values := make([]any, len(cols))
		ptrs := make([]any, len(cols))
		for i := range values {
			ptrs[i] = &values[i]
		}
		if err := rows.Scan(ptrs...); err != nil {
			return nil, err
		}
		row := make(map[string]any, len(cols))
		for i, c := range cols {
			row[c] = jsonSafe(values[i])
		}
		out = append(out, row)
	}
	if out == nil {
		out = []map[string]any{}
	}
	return out, rows.Err()
}

// jsonSafe converts driver values that don't marshal cleanly to JSON. DuckDB's
// Go driver returns most numerics natively, but a few types need help:
//   - DECIMAL comes back as duckdb.Decimal{Width, Scale, *big.Int}, which would
//     serialize as an ugly struct; render it as a string, matching the Node and
//     Python demos (so `revenue` is a string like "125000.50").
//   - []byte (e.g. BLOB) becomes a string instead of base64.
func jsonSafe(v any) any {
	switch t := v.(type) {
	case duckdb.Decimal:
		return t.String()
	case []byte:
		return string(t)
	default:
		return v
	}
}
