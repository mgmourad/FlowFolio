from typer.testing import CliRunner

from flowfolio_converter.cli import app
from flowfolio_converter.version import __version__


def test_version_command_prints_version() -> None:
    result = CliRunner().invoke(app, ["version"])
    assert result.exit_code == 0
    assert __version__ in result.output
