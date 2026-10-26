#!/usr/bin/env python3
"""Export an explicit allowlist; never package APKs, private/ or raw analysis."""
from pathlib import Path
import hashlib
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TOP = ["README.md", "LICENSE", ".gitignore", "BUILD.md", "VALIDATION.md",
       "ANALYSIS.md", "PRIVACY.md", "NFC-FINDINGS.md", "LIVE-OBSERVATION.md", "VENDOR-FINDINGS.md", "LOGIN-INTEGRATION.md", "measurement-template.md", "page-entry-findings.md",
       "repository-review.md", "inspect_manifest.py"]
EXTENSIONS = {".java", ".xml", ".py", ".md", ".txt"}


def main():
    files = [ROOT / name for name in TOP]
    for name in ["app", "tests", "tools"]:
        for p in (ROOT / name).rglob("*"):
            if p.is_file() and p.suffix in EXTENSIONS: files.append(p)
    for p in files:
        if p.is_symlink() or not p.is_file() or ROOT not in p.resolve().parents:
            raise RuntimeError("Unsafe or missing source file: " + str(p))
    out = ROOT / "dist"; out.mkdir(exist_ok=True)
    version = ET.parse(ROOT / "app/AndroidManifest.xml").getroot().get("{http://schemas.android.com/apk/res/android}versionName")
    if not version or any(c not in "0123456789." for c in version):
        raise RuntimeError("Unexpected versionName")
    archive = out / ("dorm-door-shortcut-source-" + version + ".zip")
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
        for p in sorted(set(files)):
            info = zipfile.ZipInfo("dorm-door-shortcut/" + str(p.relative_to(ROOT)), (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, p.read_bytes())
    with zipfile.ZipFile(archive) as z:
        for name in z.namelist():
            parts = Path(name).parts
            assert not any(x in parts for x in ["private", "build", "dist", "raw-logs", "recordings"])
            assert not name.endswith((".apk", ".dex", ".jks", ".keystore", ".class"))
        print("Source-only archive verified:", len(z.namelist()), "files")
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    (out / "SOURCE_SHA256SUMS").write_text(digest + "  " + archive.name + "\n")
    print(archive)
    print("SHA256:", digest)


if __name__ == "__main__": main()
