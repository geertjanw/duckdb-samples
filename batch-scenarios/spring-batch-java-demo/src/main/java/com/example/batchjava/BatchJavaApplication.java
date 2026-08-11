package com.example.batchjava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Traditional batch transformation: the transform step loops over rows one at a
 * time and folds them into in-memory Java collections. Compare the wall-clock
 * time it prints against the sibling spring-batch-duckdb-demo, which hands the
 * exact same transformation to DuckDB's vectorized engine.
 */
@SpringBootApplication
public class BatchJavaApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(BatchJavaApplication.class, args)));
    }
}
