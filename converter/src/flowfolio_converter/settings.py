"""Runtime configuration. All values come from environment variables (prefix FLOWFOLIO_)."""

from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="FLOWFOLIO_", env_file=".env", extra="ignore")

    environment: Literal["dev", "test", "prod"] = "dev"
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR"] = "INFO"
    data_dir: Path = Path("data")
    """Root for job storage when using the local filesystem backend."""
    max_upload_mb: int = 500


@lru_cache
def get_settings() -> Settings:
    return Settings()
