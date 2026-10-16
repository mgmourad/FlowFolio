"""Run the Android Gradle wrapper on any OS (used by pre-commit hooks).

Usage: python infra/ci/gradle.py <gradle args...>
Picks gradlew.bat on Windows and gradlew elsewhere, running from android/.
"""

import subprocess
import sys
from pathlib import Path

ANDROID_DIR = Path(__file__).resolve().parents[2] / "android"


def main() -> int:
    wrapper = "gradlew.bat" if sys.platform == "win32" else "./gradlew"
    cmd = [str(ANDROID_DIR / wrapper) if sys.platform == "win32" else wrapper, *sys.argv[1:]]
    return subprocess.call(cmd, cwd=ANDROID_DIR)  # noqa: S603 - fixed executable, args from hook config


if __name__ == "__main__":
    sys.exit(main())
