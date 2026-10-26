#!/usr/bin/env python3
"""Download pinned official SDK archives to private/toolchain; no system changes.

Read Google's Android SDK terms before running:
https://developer.android.com/studio/terms
This script does not run sdkmanager --licenses or install system packages.
"""
from pathlib import Path
import hashlib
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGES = [
    ("build-tools_r35_linux.zip", "bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88"),
    ("platform-36_r02.zip", "37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e"),
]


def main():
    target = ROOT / "private/toolchain"
    if target.exists(): raise SystemExit("Toolchain already exists; refusing to overwrite")
    target.mkdir(parents=True, mode=0o700)
    for name, digest in PACKAGES:
        archive = target / name
        subprocess.run(["curl", "-L", "--fail", "--connect-timeout", "10", "--max-time", "180",
                        "https://dl.google.com/android/repository/" + name, "-o", str(archive)], check=True)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
            raise RuntimeError("Checksum mismatch: " + name)
        with zipfile.ZipFile(archive) as z:
            for item in z.infolist():
                if target.resolve() not in (target / item.filename).resolve().parents:
                    raise RuntimeError("Unsafe archive path")
                if item.external_attr >> 16 & 0o170000 == 0o120000:
                    raise RuntimeError("Symlink entries not supported")
            z.extractall(target)
    for name in ["aapt2", "aapt", "d8", "zipalign", "apksigner"]:
        (target / "android-15" / name).chmod(0o755)
    print("SDK ready. Build with:")
    print("python3 tools/build.py --tools private/toolchain/android-15 --android-jar private/toolchain/android-36/android.jar")


if __name__ == "__main__": main()
