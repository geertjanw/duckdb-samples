package com.example.recon;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "fetch", description = "Download the day's exports")
class Fetch implements Callable<Integer> {

    @Option(names = "--date", required = true) String date;
    @Option(names = "--bucket", defaultValue = "finance-drops") String bucket;

    @Override
    public Integer call() {
        // Replace with your S3 client. This stub only shows the shape.
        System.out.println("would download s3://" + bucket + "/" + date + "/ to drops/" + date);
        return 0;
    }
}
