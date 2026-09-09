package com.example.recon;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "recon",
    mixinStandardHelpOptions = true,
    version = "recon 0.1.0",
    description = "Order-to-cash reconciliation over daily exports",
    subcommands = { Fetch.class, Check.class, Report.class, Sql.class }
)
public class Recon {
    public static void main(String[] args) {
        int code = new CommandLine(new Recon()).execute(args);
        System.exit(code);
    }
}
