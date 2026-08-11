package com.example.batchjava;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;

/**
 * A two-step Spring Batch job:
 *   1. generate — create the deterministic orders.csv if it is missing (setup).
 *   2. transform — the timed, in-memory Java aggregation.
 */
@Configuration
class BatchConfig {

    @Value("${app.rows}")
    private long rows;

    @Value("${app.data-dir}")
    private String dataDir;

    private Path ordersCsv() {
        return Path.of(dataDir, "orders.csv");
    }

    private Path summaryCsv() {
        return Path.of(dataDir, "summary-java.csv");
    }

    @Bean
    Tasklet generateTasklet() {
        return (contribution, chunkContext) -> {
            OrdersCsvGenerator.generateIfMissing(ordersCsv(), rows);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    Step generateStep(JobRepository jobRepository, PlatformTransactionManager tx, Tasklet generateTasklet) {
        return new StepBuilder("generate", jobRepository)
                .tasklet(generateTasklet, tx)
                .build();
    }

    @Bean
    Step transformStep(JobRepository jobRepository, PlatformTransactionManager tx) {
        return new StepBuilder("transform", jobRepository)
                .tasklet(new JavaTransformTasklet(ordersCsv(), summaryCsv(), rows), tx)
                .build();
    }

    @Bean
    Job batchJavaJob(JobRepository jobRepository, Step generateStep, Step transformStep) {
        return new JobBuilder("batchJavaJob", jobRepository)
                .start(generateStep)
                .next(transformStep)
                .build();
    }
}
