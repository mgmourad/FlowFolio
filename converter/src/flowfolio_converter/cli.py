"""Command-line entry point: `flowfolio-convert`.

Pipeline stages (A-I in docs/architecture.md) are added as subcommands from Phase 3 on,
so each stage can be run on its own for debugging.
"""

from typing import Annotated

import typer
import uvicorn

from flowfolio_converter.version import SPEC_VERSION, __version__

app = typer.Typer(help="FlowFolio converter CLI.", no_args_is_help=True)


@app.command()
def version() -> None:
    """Print the converter and spec versions."""
    typer.echo(f"flowfolio-converter {__version__} (spec {SPEC_VERSION})")


@app.command()
def serve(
    host: Annotated[str, typer.Option(help="Bind address.")] = "127.0.0.1",
    port: Annotated[int, typer.Option(help="Bind port.")] = 8000,
    reload: Annotated[bool, typer.Option(help="Auto-reload on code changes (dev).")] = False,
) -> None:
    """Run the HTTP API."""
    uvicorn.run("flowfolio_converter.api.app:app", host=host, port=port, reload=reload)


if __name__ == "__main__":
    app()
