package com.example.duckdbodbc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class DemoController {

    private final OdbcQueryService odbc;
    private final BenchmarkService benchmark;

    public DemoController(OdbcQueryService odbc, BenchmarkService benchmark) {
        this.odbc = odbc;
        this.benchmark = benchmark;
    }

    /** Top customers, computed inside MySQL, fetched through ODBC. */
    @GetMapping("/customers/top")
    public List<Map<String, Object>> topCustomers(@RequestParam(defaultValue = "10") int limit) {
        return odbc.topCustomers(limit);
    }

    /** MySQL rows aggregated by DuckDB - the hybrid query demo. */
    @GetMapping("/customers/revenue-by-country")
    public List<Map<String, Object>> revenueByCountry() {
        return odbc.revenueByCountry();
    }

    /** Stretch goal: generate and load N rows into MySQL (default 100M). */
    @PostMapping("/benchmark/load")
    public Map<String, Object> load(@RequestParam(defaultValue = "100000000") long rows) {
        return benchmark.generateAndLoadOrders(rows);
    }

    /** Benchmark the aggregation through the ODBC extension. */
    @GetMapping("/benchmark/odbc")
    public Map<String, Object> benchOdbc() {
        return benchmark.benchmarkOdbc();
    }

    /** Benchmark the same aggregation through the native MySQL scanner. */
    @GetMapping("/benchmark/scanner")
    public Map<String, Object> benchScanner() {
        return benchmark.benchmarkMySqlScanner();
    }
}
