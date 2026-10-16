# syntax=docker/dockerfile:1

# ---- build stage: resolve and install into /app/.venv with uv ----
FROM python:3.14-slim AS build
COPY --from=ghcr.io/astral-sh/uv:0.13.1 /uv /usr/local/bin/uv
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never
WORKDIR /app
# Dependencies first for layer caching
COPY pyproject.toml uv.lock README.md ./
RUN --mount=type=cache,target=/root/.cache/uv uv sync --locked --no-dev --no-install-project
COPY src ./src
RUN --mount=type=cache,target=/root/.cache/uv uv sync --locked --no-dev

# ---- runtime stage ----
FROM python:3.14-slim AS runtime
ARG GIT_SHA=unknown
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    FLOWFOLIO_GIT_SHA=${GIT_SHA} \
    FLOWFOLIO_DATA_DIR=/data
RUN useradd --create-home --uid 10001 flowfolio && mkdir /data && chown flowfolio /data
WORKDIR /app
COPY --from=build --chown=flowfolio /app /app
USER flowfolio
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD ["python", "-c", "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/healthz').status == 200 else 1)"]
CMD ["uvicorn", "flowfolio_converter.api.app:app", "--host", "0.0.0.0", "--port", "8000"]
