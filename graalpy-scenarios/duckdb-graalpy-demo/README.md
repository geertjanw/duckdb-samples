# duckdb-graalpy-demo

DuckDB and Python in one JVM process. Inspired by Graeme Rocher's PyCon FR 2026
talk [*Fusing Python with Java using GraalPy and Micronaut*](https://www.pycon.fr/2026/en/talks/long-talk.html#talk-3JHBZY),
which runs Python inside the JVM so it sits alongside Java libraries in the same
application. Here the Java library is **DuckDB**.

The division of labour:

- **DuckDB** (a plain Java library, via JDBC) generates 1,000 sales rows and runs
  the vectorized `GROUP BY` over the whole dataset.
- **Python** (on **GraalPy**, embedded through the GraalVM Polyglot API) takes
  the small aggregated result and applies the business rules — tiering each
  region and formatting the report.

The aggregated rows pass straight from DuckDB into Python as a Java array: same
process, no REST call, no subprocess, no JSON in between.

| File | Role |
|---|---|
| [`Main.java`](src/main/java/org/example/Main.java) | Runs DuckDB over JDBC, then calls into Python with the result |
| [`report.py`](src/main/resources/report.py) | The business logic: region tiers and the formatted report, in Python |
| [`pom.xml`](pom.xml) | `duckdb_jdbc` + GraalVM `polyglot` / `python-community` |

## Run it

Needs JDK 17 or newer and Maven. The first build downloads the GraalPy runtime,
which is large.

```bash
mvn -q exec:java
```

Output (the table is built by Python, from DuckDB's aggregation):

```
Region  Orders    Revenue  AvgOrder  Tier
------------------------------------------
West       256   13527.89     52.84  B
South      251   13290.71     52.95  B
North      248   12799.40     51.61  B
East       245   12155.74     49.62  B
------------------------------------------
Total revenue across 4 regions: 51773.74
```

## What to notice

- **One process, two ecosystems.** DuckDB's JDBC driver and the Python
  interpreter run in the same JVM. Python reads the Java `Object[][]` directly
  (`allowAllAccess` on the polyglot context makes that possible).
- **Each side does what it's best at.** DuckDB handles the data-volume work; the
  expressive per-row rules stay in readable Python. Moving the rules between the
  two is editing one file, not crossing a network boundary.
- **No external Python packages.** `report.py` uses only builtins, so GraalPy
  needs nothing installed. To use PyPI packages, GraalPy manages a venv through
  the `python-embedding` / `graalpy-maven-plugin` tooling.

On a stock JDK, GraalPy runs in interpreter mode (the demo silences the
performance warning in `pom.xml`). On GraalVM it compiles the Python to native
code through the same JIT as the Java. Micronaut, from the talk, is the web
framework that would wrap this into an HTTP service.
