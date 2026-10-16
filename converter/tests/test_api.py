from fastapi.testclient import TestClient

from flowfolio_converter.api.app import create_app
from flowfolio_converter.settings import Settings
from flowfolio_converter.version import SPEC_VERSION, __version__


def _client() -> TestClient:
    return TestClient(create_app(Settings(environment="test")))


def test_healthz_returns_ok() -> None:
    response = _client().get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_version_reports_package_and_spec() -> None:
    body = _client().get("/version").json()
    assert body["version"] == __version__
    assert body["spec_version"] == SPEC_VERSION
    assert body["environment"] == "test"
