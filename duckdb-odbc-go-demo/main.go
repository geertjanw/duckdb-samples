// HTTP entry point and routes - the Go analogue of DemoController plus
// DuckdbOdbcApplication.
//
// The ODBC connection is opened at startup and the process exits if MySQL is
// not reachable, matching the Java demo's fail-fast @PostConstruct behavior.
package main

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strconv"

	"github.com/example/duckdb-odbc-go-demo/internal/demo"
)

func main() {
	cfg := demo.LoadConfig()

	db, err := demo.OpenDuckDB(cfg.DuckDBDatabase)
	if err != nil {
		log.Fatalf("Startup failed: %v", err)
	}
	defer db.Close()

	odbc := demo.NewOdbcQueryService(db, cfg)
	bench := demo.NewBenchmarkService(db, cfg)

	// Open the ODBC connection at startup; fail fast if MySQL isn't reachable.
	if err := odbc.Init(context.Background()); err != nil {
		log.Fatalf("Startup failed: %v", err)
	}

	mux := http.NewServeMux()

	// Top customers, computed inside MySQL, fetched through ODBC.
	mux.HandleFunc("GET /customers/top", func(w http.ResponseWriter, r *http.Request) {
		limit := queryInt(r, "limit", 10)
		writeJSON(w, func() (any, error) { return odbc.TopCustomers(r.Context(), limit) })
	})

	// MySQL rows aggregated by DuckDB - the hybrid query demo.
	mux.HandleFunc("GET /customers/revenue-by-country", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, func() (any, error) { return odbc.RevenueByCountry(r.Context()) })
	})

	// Stretch goal: generate and load N rows into MySQL (default 100M).
	mux.HandleFunc("POST /benchmark/load", func(w http.ResponseWriter, r *http.Request) {
		rows := int64(queryInt(r, "rows", 100_000_000))
		writeJSON(w, func() (any, error) { return bench.GenerateAndLoadOrders(r.Context(), rows) })
	})

	// Benchmark the aggregation through the ODBC extension.
	mux.HandleFunc("GET /benchmark/odbc", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, func() (any, error) { return bench.BenchmarkODBC(r.Context()) })
	})

	// Benchmark the same aggregation through the native MySQL scanner.
	mux.HandleFunc("GET /benchmark/scanner", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, func() (any, error) { return bench.BenchmarkMySQLScanner(r.Context()) })
	})

	// Benchmark the same aggregation on the local DuckDB table (no transfer).
	mux.HandleFunc("GET /benchmark/native", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, func() (any, error) { return bench.BenchmarkNative(r.Context()) })
	})

	addr := ":" + cfg.Port
	log.Printf("Listening on http://localhost:%s", cfg.Port)
	if err := http.ListenAndServe(addr, mux); err != nil {
		if errors.Is(err, http.ErrServerClosed) {
			return
		}
		log.Fatalf("server error: %v", err)
	}
}

// writeJSON runs fn and encodes its result, surfacing any DuckDB/ODBC error as
// a 500 JSON body (matching the other demos' error shape).
func writeJSON(w http.ResponseWriter, fn func() (any, error)) {
	result, err := fn()
	w.Header().Set("Content-Type", "application/json")
	if err != nil {
		log.Println(err)
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}
	_ = json.NewEncoder(w).Encode(result)
}

func queryInt(r *http.Request, key string, fallback int) int {
	if v := r.URL.Query().Get(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return fallback
}
