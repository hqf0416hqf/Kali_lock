#!/usr/bin/env python3
"""Offline Android build using an existing SDK; no Gradle or network required.

Usage: python3 tools/build.py --tools SDK/build-tools/35.0.0 --android-jar SDK/platforms/android-36/android.jar
Creates a LOCAL development key in private/ (never include in source archives).
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def run(args):
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tools", required=True, type=Path)
    parser.add_argument("--android-jar", required=True, type=Path)
    options = parser.parse_args()
    tools, jar = options.tools.resolve(), options.android_jar.resolve()
    for executable in ["aapt2", "d8", "zipalign", "apksigner"]:
        if not (tools / executable).is_file(): parser.error("Missing SDK tool: " + executable)
    if not jar.is_file(): parser.error("android.jar not found")
    compiler = ["javac"] if shutil.which("javac") else ["java", "-m", "jdk.compiler/com.sun.tools.javac.Main"]
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    # Generated subdirectories only: never remove project sources or private keys.
    for name in ["classes", "gen", "dex", "test-classes"]:
        folder = build / name
        if folder.is_symlink(): raise RuntimeError("Refusing build symlink")
        if folder.exists(): shutil.rmtree(folder)
        folder.mkdir()
    run([*compiler, "--release", "8", "-encoding", "UTF-8", "-d", build / "test-classes",
         ROOT / "app/src/local/dorm/shortcut/Flow.java", ROOT / "tests/FlowTest.java"])
    run(["java", "-cp", build / "test-classes", "FlowTest"])
    run([tools / "aapt2", "compile", "--dir", ROOT / "app/res", "-o", build / "resources.zip"])
    run([tools / "aapt2", "link", "-o", build / "resources.apk", "-I", jar,
         "--manifest", ROOT / "app/AndroidManifest.xml", "--java", build / "gen",
         build / "resources.zip"])
    sources = sorted((ROOT / "app/src").rglob("*.java")) + sorted((build / "gen").rglob("*.java"))
    run([*compiler, "--release", "8", "-encoding", "UTF-8", "-classpath", jar,
         "-d", build / "classes", *sources])
    class_jar = build / "classes.jar"
    with zipfile.ZipFile(class_jar, "w", zipfile.ZIP_DEFLATED) as z:
        for p in sorted((build / "classes").rglob("*.class")):
            z.write(p, p.relative_to(build / "classes"))
    run([tools / "d8", "--min-api", "26", "--lib", jar, "--output", build / "dex", class_jar])
    unsigned = build / "unsigned.apk"
    shutil.copyfile(build / "resources.apk", unsigned)
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
        for p in sorted((build / "dex").glob("*.dex")): z.write(p, p.name)
    run([tools / "zipalign", "-f", "4", unsigned, build / "aligned.apk"])
    private = ROOT / "private"
    private.mkdir(mode=0o700, exist_ok=True)
    key = private / "development-signing.jks"
    if not key.exists():
        # Publicly known development password. This key is NOT a release identity.
        run(["keytool", "-genkeypair", "-keystore", key, "-storepass", "android", "-keypass", "android",
             "-alias", "development", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
             "-dname", "CN=Local Development, O=Dorm Shortcut, C=ZZ"])
        key.chmod(0o600)
    dist = ROOT / "dist"; dist.mkdir(exist_ok=True)
    version = ET.parse(ROOT / "app/AndroidManifest.xml").getroot().get("{http://schemas.android.com/apk/res/android}versionName")
    if not version or any(c not in "0123456789." for c in version):
        raise RuntimeError("Unexpected versionName")
    apk = dist / ("dorm-door-shortcut-" + version + ".apk")
    run([tools / "apksigner", "sign", "--ks", key, "--ks-pass", "pass:android", "--key-pass", "pass:android",
         "--out", apk, build / "aligned.apk"])
    run([tools / "apksigner", "verify", "--verbose", apk])
    run([tools / "zipalign", "-c", "4", apk])
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    (dist / "SHA256SUMS").write_text(digest + "  " + apk.name + "\n")
    print("Built:", apk)
    print("SHA256:", digest)


if __name__ == "__main__":
    main()
