# Presentation outline

**Title:** dbt without the warehouse (or the bill): DuckDB end to end
**Subtitle:** A dbt project developed, tested, deployed, and served entirely on DuckDB
**Length:** ~20 minutes (≈12 talk + ≈6 live demo + ≈2 buffer/Q&A)

The whole talk exists to earn one sentence from the abstract:

> *You'll see a real dbt project developed, tested, deployed, and served entirely
> on DuckDB, live on stage, on more data than you'd think reasonable.*

Everything the audience sees maps to the code in
[`../end-to-end-duckdb`](../end-to-end-duckdb) (the demo scenario) and its four
demo commands. No slideware invents anything the repo can't do live.

## Arc

| # | Slide | Beat | ~min |
|---|---|---|---|
| 1 | dbt without the warehouse (or the bill) | Title — the promise | 0:30 |
| 2 | We rented a distributed system… | The absurdity, named | 0:45 |
| 3 | Every dbt run is a round-trip you didn't need | The three taxes: slow, metered, queued | 1:00 |
| 4 | A few hundred GB — a strange way to live; you can stop | The thesis | 0:45 |
| 5 | DuckDB | The tool: free, in-process, on Fusion beta | 1:15 |
| 6 | What that unlocks | Fast iteration, cheap CI, in-process Python | 1:15 |
| 7 | The warehouse becomes optional — at every stage | Parquet/Iceberg readers, DuckLake, WASM | 1:15 |
| 8 | Let's build one. Live. | Transition to terminal | 0:30 |
| 9 | **1 · Develop** — `dbt run` | Build 3 models + a Python model from a URL | 1:30 |
| 10 | **2 · Test** — `dbt test` | Schema tests + the card-vs-cash assertion | 1:30 |
| 11 | **3 · Deploy** — open Parquet | `publish_*` wrote `web/*.parquet`; DuckLake in prod | 1:15 |
| 12 | **4 · Serve** — DuckDB-WASM | The browser page queries the Parquet, no backend | 1:30 |
| 13 | The receipts | ~3M rows, seconds, ms scan, $0.00 | 1:00 |
| 14 | Your data is not that big / your bill either | Close + repo pointer | 0:45 |

## Abstract coverage

Every sentence of the abstract has a home in the deck (and, where it's a
capability, in the demo code):

| Abstract clause | Slide(s) | In the code? |
|---|---|---|
| "…renting a distributed system by the second." | 2 | — |
| "Every dbt run… slow, metered, and queued behind your colleagues' CI jobs." | 3 | — |
| "If your whole project fits in a few hundred GB… you can stop." | 4 | — |
| "DuckDB is a free, MIT-licensed, in-process… no server, no account, no invoice… public beta on the dbt Fusion engine." | 5 | dbt-duckdb adapter (`profiles.yml`) |
| "sub-second model iteration… CI in seconds… Python models that run in-process without your data going anywhere." | 6 | `models/trips_by_hour.py` (in-process Python) |
| "native readers for Parquet, Iceberg, and Postgres" | 7 | Parquet in `stg_trips.sql`; Iceberg/Postgres noted in the demo README |
| "DuckLake for publishing your output tables in open formats" | 7, 11 | `publish_*.sql` (external Parquet); DuckLake noted as the prod version |
| "DuckDB-WASM for querying them straight from the browser" | 7, 12 | `web/index.html` |
| "the warehouse becomes optional at every stage — not just development." | 7 | the whole develop→test→deploy→serve flow |
| "developed, tested, deployed, and served entirely on DuckDB… more data than you'd think reasonable." | 8–13 | the four demo commands |
| "Your data is not that big. Your bill doesn't have to be either." | 14 | — |

## The four demo commands (slides 9–12)

```bash
cd dbt-scenarios/end-to-end-duckdb
export DBT_PROFILES_DIR=.

dbt run                                   # 1 · DEVELOP  (+ writes web/*.parquet = DEPLOY)
dbt test                                  # 2 · TEST
cd web && python3 -m http.server 8000     # 4 · SERVE — open http://localhost:8000
```

Deploy (slide 11) needs no extra command: `dbt run` already materialized the two
`publish_*` models as external Parquet into `web/`.

## Design notes

- Brand: DuckDB yellow `#FFF000` on near-black, matching `web/index.html`.
- The deck ships as three files: `deck.html` (recommended for presenting),
  `deck.pptx`, and `deck.pdf`. To edit, change `deck.html` and re-export the PDF/pptx.
  Speaker notes live in `deck.html` (the **s** key) and `speaker-notes.md` — not in
  `deck.pptx`, which is image-only so Keynote imports it.
- The title slide leaves a placeholder for the official DuckDB duck mark — drop the
  logo in before presenting.
