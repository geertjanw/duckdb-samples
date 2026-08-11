// Benchmark:
//  1. Generate a large orders table and load it into MySQL/MariaDB.
//  2. Benchmark the same aggregation through
//     (a) the ODBC extension, (b) the native MySQL scanner extension, and
//     (c) a local DuckDB table as the no-transfer baseline.
//
// Uses the DuckDB core `odbc_scanner` extension API:
//   - odbc_query(conn, sql)  -- table function; runs any SQL (incl. DDL) in the remote DB
//   - odbc_copy(conn, ...)   -- bulk-loads rows into the remote DB over ODBC,
//     batching inserts (up to 2048 rows per SQLExecute)
//
// Note: there is no odbc_exec in this extension; DDL also goes through odbc_query.
//
// MariaDB/MySQL specifics encountered along the way:
//   - odbc_copy's create_table=true fails ("column type not recognized:
//     DUCKDB_TYPE_BIGINT"): the automatic type mapping is incomplete for
//     MySQL/MariaDB (Tier 2 support). We create the table explicitly instead,
//     which also lets us keep the PRIMARY KEY.
//   - odbc_copy quotes column names with double quotes by default, which MariaDB
//     rejects in its default sql_mode (it expects backticks), hence column_quotes = '`'.
package demo

import (
	"context"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// BenchmarkService owns the bulk-load and the three benchmark paths.
type BenchmarkService struct {
	db  *DuckDB
	cfg Config

	scannerOnce sync.Once
	scannerErr  error
}

func NewBenchmarkService(db *DuckDB, cfg Config) *BenchmarkService {
	return &BenchmarkService{db: db, cfg: cfg}
}

// LoadReport is the per-phase timing report returned by the load endpoint.
type LoadReport struct {
	GenerateSeconds  float64 `json:"generate_seconds"`
	StageCSVSeconds  float64 `json:"stage_csv_seconds"`
	MySQLLoadSeconds float64 `json:"mysql_load_seconds"`
	Rows             int64   `json:"rows"`
}

// BenchResult is the shape returned by each benchmark path.
type BenchResult struct {
	Path    string           `json:"path"`
	Seconds float64          `json:"seconds"`
	Top5    []map[string]any `json:"top5"`
}

// GenerateAndLoadOrders generates `rows` orders into a local DuckDB table (which
// doubles as the baseline for the native benchmark), stages them as CSV, and
// bulk-loads them into MySQL with odbc_copy. Row-by-row INSERTs over ODBC would
// take forever at this scale; odbc_copy batches up to 2048 rows per round trip
// and wraps the whole load in a remote transaction.
//
// The CSV detour exists because odbc_copy runs its source query in a separate
// DuckDB instance and cannot see orders_local directly. The CSV column order
// must match the remote table column order.
func (s *BenchmarkService) GenerateAndLoadOrders(ctx context.Context, rows int64) (LoadReport, error) {
	var report LoadReport
	report.Rows = rows
	csvPath := filepath.Join(os.TempDir(), "orders.csv")

	// 1. Generate into a local table: the native benchmark scans this.
	t0 := time.Now()
	if err := s.db.Exec(ctx, fmt.Sprintf(`
        CREATE OR REPLACE TABLE orders_local AS
        SELECT range AS id,
               (range %% 1000)::INTEGER AS customer_id,
               round(random() * 100, 2) AS amount
        FROM range(%d)`, rows)); err != nil {
		return report, fmt.Errorf("generate orders_local: %w", err)
	}
	report.GenerateSeconds = secondsSince(t0)

	// 2. Stage as CSV for odbc_copy.
	t1 := time.Now()
	if err := s.db.Exec(ctx,
		fmt.Sprintf("COPY orders_local TO '%s' (FORMAT CSV, HEADER true)", csvPath),
	); err != nil {
		return report, fmt.Errorf("stage csv: %w", err)
	}
	report.StageCSVSeconds = secondsSince(t1)

	// 3. Remote DDL goes through odbc_query (a table function, hence FROM ...).
	//    Explicit CREATE TABLE because create_table=true fails for MySQL/MariaDB
	//    ("column type not recognized: DUCKDB_TYPE_BIGINT").
	if err := s.db.Exec(ctx,
		"FROM odbc_query(getvariable('conn'), 'DROP TABLE IF EXISTS orders')",
	); err != nil {
		return report, fmt.Errorf("drop remote orders: %w", err)
	}
	if err := s.db.Exec(ctx, `
        FROM odbc_query(getvariable('conn'),
            'CREATE TABLE orders (
                 id BIGINT PRIMARY KEY,
                 customer_id INT,
                 amount DOUBLE)')`); err != nil {
		return report, fmt.Errorf("create remote orders: %w", err)
	}

	// 4. Bulk-load over ODBC. column_quotes: MariaDB's default sql_mode rejects
	//    double-quoted identifiers, so quote with backticks.
	t2 := time.Now()
	if err := s.db.Exec(ctx, fmt.Sprintf(`
        FROM odbc_copy(getvariable('conn'),
            source_file = '%s',
            dest_table = 'orders',
            batch_size = 2048,
            column_quotes = '`+"`"+`')`, csvPath)); err != nil {
		return report, fmt.Errorf("odbc_copy: %w", err)
	}
	report.MySQLLoadSeconds = secondsSince(t2)
	log.Printf("Loaded %d rows into MySQL: %+v", rows, report)
	return report, nil
}

// BenchmarkODBC aggregates pushed through the ODBC extension (row-by-row transfer).
func (s *BenchmarkService) BenchmarkODBC(ctx context.Context) (BenchResult, error) {
	t0 := time.Now()
	result, err := s.db.Query(ctx, `
        SELECT customer_id, sum(amount) AS total
        FROM odbc_query(getvariable('conn'),
             'SELECT customer_id, amount FROM orders')
        GROUP BY customer_id
        ORDER BY total DESC
        LIMIT 5`)
	if err != nil {
		return BenchResult{}, err
	}
	return BenchResult{Path: "odbc_extension", Seconds: secondsSince(t0), Top5: result}, nil
}

// BenchmarkMySQLScanner runs the same aggregation through the native MySQL scanner.
func (s *BenchmarkService) BenchmarkMySQLScanner(ctx context.Context) (BenchResult, error) {
	if err := s.attachScanner(ctx); err != nil {
		return BenchResult{}, err
	}
	t0 := time.Now()
	result, err := s.db.Query(ctx, `
        SELECT customer_id, sum(amount) AS total
        FROM mysqldb.orders
        GROUP BY customer_id
        ORDER BY total DESC
        LIMIT 5`)
	if err != nil {
		return BenchResult{}, err
	}
	return BenchResult{Path: "mysql_scanner", Seconds: secondsSince(t0), Top5: result}, nil
}

// BenchmarkNative runs the same aggregation on the local DuckDB table: the
// no-transfer baseline. Same rows as MySQL holds (loaded from the same
// generation), so the top-5 output matches the other two paths for a load.
func (s *BenchmarkService) BenchmarkNative(ctx context.Context) (BenchResult, error) {
	t0 := time.Now()
	result, err := s.db.Query(ctx, `
        SELECT customer_id, sum(amount) AS total
        FROM orders_local
        GROUP BY customer_id
        ORDER BY total DESC
        LIMIT 5`)
	if err != nil {
		return BenchResult{}, err
	}
	return BenchResult{Path: "native_duckdb", Seconds: secondsSince(t0), Top5: result}, nil
}

// attachScanner attaches the mysql scanner lazily and exactly once: attaching
// twice with the same alias fails. READ_ONLY because the benchmark only reads
// and it lets DuckDB skip transaction bookkeeping on the attached database.
func (s *BenchmarkService) attachScanner(ctx context.Context) error {
	s.scannerOnce.Do(func() {
		if s.scannerErr = s.db.Exec(ctx, "INSTALL mysql"); s.scannerErr != nil {
			return
		}
		if s.scannerErr = s.db.Exec(ctx, "LOAD mysql"); s.scannerErr != nil {
			return
		}
		s.scannerErr = s.db.Exec(ctx, fmt.Sprintf(
			"ATTACH '%s' AS mysqldb (TYPE mysql, READ_ONLY)",
			s.cfg.ScannerConnectionString,
		))
		if s.scannerErr == nil {
			log.Println("MySQL scanner attached.")
		}
	})
	return s.scannerErr
}

func secondsSince(start time.Time) float64 {
	// Round to 2 decimals to match the other demos' reports.
	return float64(int64(time.Since(start).Seconds()*100+0.5)) / 100
}
