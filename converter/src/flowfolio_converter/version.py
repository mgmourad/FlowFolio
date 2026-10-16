"""Version information. `__version__` comes from installed package metadata."""

import os
from importlib.metadata import PackageNotFoundError, version

try:
    __version__ = version("flowfolio-converter")
except PackageNotFoundError:  # pragma: no cover - only when running from an uninstalled tree
    __version__ = "0.0.0+unknown"

SPEC_VERSION = "0.1.0"
"""Version of docs/conversion-spec.md this build implements."""


def git_sha() -> str:
    """Commit the image or checkout was built from; injected at Docker build time."""
    return os.environ.get("FLOWFOLIO_GIT_SHA", "unknown")
