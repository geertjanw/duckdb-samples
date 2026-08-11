package com.example.batchduckdb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The same batch transformation as spring-batch-java-demo, but the transform
 * step hands the whole aggregation to DuckDB's vectorized engine in a single
 * SQL statement instead of looping over rows in Java. Compare the wall-clock
 * time it prints.
 */
@SpringBootApplication
public class BatchDuckdbApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(BatchDuckdbApplication.class, args)));
    }
}
