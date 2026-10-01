#!/usr/bin/env python3
"""Build and package Android: python tools/ci_build.py android release."""
import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

def run(command, cwd=ROOT):
    print("+", " ".join(map(str, command)), flush=True)
    subprocess.run(list(map(str, command)), cwd=cwd, check=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=["android"])
    parser.add_argument("config", choices=["debug", "release"])
    args = parser.parse_args()
    command = [sys.executable, "configure.py"]
    if args.config == "release":
        command.append("--release")
    else:
        command.append("--pgo=off")
    if os.environ.get("CI_COMPILER_LAUNCHER"):
        command += ["--compiler-launcher", os.environ["CI_COMPILER_LAUNCHER"]]
    if os.environ.get("GITHUB_REF") == "refs/heads/main" and os.environ.get("GITHUB_RUN_NUMBER", "").isdigit():
        os.environ["HALO_BUILD_NUMBER"] = os.environ["GITHUB_RUN_NUMBER"]
    run(command)
    run(["ninja", "android"])
    if os.name == "nt":
        run([sys.executable, "tools/android_windows_gradle.py", args.config])
    else:
        run(["./gradlew", "--console=plain", f"assemble{args.config.capitalize()}"], ROOT / "port/android")
    dist = ROOT / "dist" / f"halo-android-{args.config}"
    dist.mkdir(parents=True, exist_ok=True)
    apk = ROOT / f"port/android/app/build/outputs/apk/{args.config}/app-{args.config}.apk"
    shutil.copy2(apk, dist / apk.name)
    for source, destination in [
        ("extract-xiso/LICENSE.TXT", "extract-xiso-LICENSE.txt"),
        ("miniupnpc/LICENSE", "miniupnpc-LICENSE.txt")
    ]:
        shutil.copy2(ROOT / "port/third_party" / source, dist / destination)

if __name__ == "__main__":
    main()
