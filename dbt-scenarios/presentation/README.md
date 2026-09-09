# presentation

Talk materials for the [`end-to-end-duckdb`](../end-to-end-duckdb) demo — **Your data is not that
big**. Everything here maps to the scenario's real code and its four demo commands;
nothing is invented for the slides.

| File | What it is |
|---|---|
| [`speaker-notes.md`](speaker-notes.md) | Full talk track, pre-flight checklist, and fallback plan |
| **`deck.html`** | **Recommended.** Self-contained browser deck — no app needed |
| `deck.pptx` | Keynote/PowerPoint version (imports into Keynote — see below) |
| `deck.pdf` | PDF export (opens anywhere; good backup) |

## Present with `deck.html` (recommended)

Just open it in any browser — double-click, or:

```bash
open deck.html      # macOS
```

Controls: **← →** (or space / click) to move, **f** for full screen, **s** to
toggle speaker notes, **Home/End** to jump. It's one self-contained file with no
dependencies, so nothing can fail to load on stage.

## About `deck.pptx`

The `.pptx` is image-per-slide: each slide is a rendered picture, so the text isn't
editable in Keynote and there's no notes pane. That's deliberate — Keynote's importer
rejects the usual programmatically-authored `.pptx`, but imports an image-based one
cleanly. Speaker notes live in `deck.html` (**s** key) and `speaker-notes.md`.

To change slide content, edit `deck.html` (it's plain HTML/CSS) and re-export to
PDF/pptx from the browser or LibreOffice; the three files are meant to stay in sync.
The title slide leaves a placeholder for the official DuckDB duck mark — add the logo
before presenting.
