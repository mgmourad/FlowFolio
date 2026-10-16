# FlowFolio Converter

Python service that converts fixed-layout PDFs into reflowable EPUB 3 plus a `flowfolio.json`
sidecar. Output contract: [`docs/conversion-spec.md`](../docs/conversion-spec.md).

Phase 0: service skeleton only (`/healthz`, `/version`, CLI).

## Commands (run from `converter/`)

```bash
uv sync                                   # create .venv from uv.lock
uv run flowfolio-convert serve --reload   # http://127.0.0.1:8000/docs
uv run pytest                             # tests
uv run ruff check . && uv run ruff format --check . && uv run mypy
docker build -t flowfolio-converter:dev .
docker run --rm -p 8000:8000 flowfolio-converter:dev
```

Configuration is via `FLOWFOLIO_*` environment variables; see `.env.example`.
