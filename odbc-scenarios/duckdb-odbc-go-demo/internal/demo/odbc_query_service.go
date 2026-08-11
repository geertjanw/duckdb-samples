// Uses the DuckDB ODBC extension (a.k.a. odbc_scanner) to query MySQL.
//
// Note the direction of the ODBC arrow:
//   - DuckDB's ODBC *client/driver* lets other apps connect INTO DuckDB.
//   - The ODBC *extension* (used here) lets DuckDB connect OUT to other
//     databases through their ODBC drivers.
package demo

import (
	"context"
	"fmt"
	"log"
)

// OdbcQueryService owns the ODBC-backed customer queries.
type OdbcQueryService struct {
	db  *DuckDB
	cfg Config
}

func NewOdbcQueryService(db *DuckDB, cfg Config) *OdbcQueryService {
	return &OdbcQueryService{db: db, cfg: cfg}
}

// Init installs/loads the odbc extension and opens the ODBC connection once.
//
// The handle is stashed in a DuckDB session variable, exactly as in the Java
// demo. Because everything runs on one pinned connection, the variable stays
// visible across every request.
func (s *OdbcQueryService) Init(ctx context.Context) error {
	log.Println("Installing and loading DuckDB odbc extension...")
	if err := s.db.Exec(ctx, "INSTALL odbc"); err != nil {
		return fmt.Errorf("install odbc: %w", err)
	}
	if err := s.db.Exec(ctx, "LOAD odbc"); err != nil {
		return fmt.Errorf("load odbc: %w", err)
	}

	// Open the ODBC connection once and stash the handle in a session variable.
	if err := s.db.Exec(ctx,
		"SET VARIABLE conn = odbc_connect(?, ?, ?)",
		s.cfg.ODBCConnectionString, s.cfg.Username, s.cfg.Password,
	); err != nil {
		return fmt.Errorf("odbc_connect: %w", err)
	}
	log.Println("Connected to MySQL via ODBC.")
	return nil
}

// queryMySQL runs an arbitrary (read-only) SQL statement on MySQL through ODBC.
func (s *OdbcQueryService) queryMySQL(ctx context.Context, mysqlSQL string) ([]map[string]any, error) {
	return s.db.Query(ctx,
		"SELECT * FROM odbc_query(getvariable('conn'), ?)", mysqlSQL)
}

// TopCustomers is a simple demo query: top customers by revenue, computed
// inside MySQL and only relayed by DuckDB.
func (s *OdbcQueryService) TopCustomers(ctx context.Context, limit int) ([]map[string]any, error) {
	// limit is an int, so it is safe to interpolate into the remote SQL string;
	// odbc_query's SQL argument is a constant expression, not a bind slot.
	return s.queryMySQL(ctx, fmt.Sprintf(`
        SELECT id, name, country, revenue
        FROM customers
        ORDER BY revenue DESC
        LIMIT %d`, limit))
}

// RevenueByCountry is DuckDB's real superpower: pull rows from MySQL over ODBC
// and aggregate them in DuckDB, in one SQL statement.
func (s *OdbcQueryService) RevenueByCountry(ctx context.Context) ([]map[string]any, error) {
	return s.db.Query(ctx, `
        SELECT country, count(*) AS customers, sum(revenue) AS total_revenue
        FROM odbc_query(getvariable('conn'),
             'SELECT country, revenue FROM customers')
        GROUP BY country
        ORDER BY total_revenue DESC`)
}
