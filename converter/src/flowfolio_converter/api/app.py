"""FastAPI application factory."""

from typing import Literal

from fastapi import FastAPI
from pydantic import BaseModel

from flowfolio_converter.settings import Settings, get_settings
from flowfolio_converter.version import SPEC_VERSION, __version__, git_sha


class Health(BaseModel):
    status: Literal["ok"] = "ok"


class VersionInfo(BaseModel):
    version: str
    spec_version: str
    git_sha: str
    environment: str


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    app = FastAPI(title="FlowFolio Converter", version=__version__)

    @app.get("/healthz", response_model=Health, tags=["ops"])
    def healthz() -> Health:
        """Liveness probe. Does not touch storage or engines."""
        return Health()

    @app.get("/version", response_model=VersionInfo, tags=["ops"])
    def version_info() -> VersionInfo:
        return VersionInfo(
            version=__version__,
            spec_version=SPEC_VERSION,
            git_sha=git_sha(),
            environment=settings.environment,
        )

    return app


app = create_app()
