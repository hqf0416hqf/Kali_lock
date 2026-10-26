"""Read only APK manifest metadata; never launch a phone activity.

Dependencies: androguard==4.1.3, loguru, lxml.
Usage: PYTHONPATH=/tmp/dorm-door-analysis-python python3 inspect_manifest.py APK
"""
import json
import sys
import zipfile

from loguru import logger
from androguard.core.axml import AXMLPrinter

logger.remove()
ANDROID = "{http://schemas.android.com/apk/res/android}"


def inspect(path):
    with zipfile.ZipFile(path) as archive:
        manifest = AXMLPrinter(archive.read("AndroidManifest.xml")).get_xml_obj()
    if manifest is None:
        raise ValueError("Manifest parsing failed")
    app = manifest.find("application")
    if app is None:
        raise ValueError("Application declaration missing")
    print(json.dumps({
        "package": manifest.get("package"),
        "version": manifest.get(ANDROID + "versionName"),
        "application_permission": app.get(ANDROID + "permission"),
    }, ensure_ascii=False))
    for node in list(app.findall("activity")) + list(app.findall("activity-alias")):
        name = node.get(ANDROID + "name", "")
        if not name.startswith("com.tmall.campus."):
            continue
        filters = node.findall("intent-filter")
        exported = node.get(ANDROID + "exported")
        if exported != "true" and not filters and not any(
            key in name.lower() for key in ("web", "door", "splash", "main", "route")
        ):
            continue
        print(json.dumps({
            "component": name,
            "exported_explicit": exported,
            "enabled_explicit": node.get(ANDROID + "enabled"),
            "permission": node.get(ANDROID + "permission"),
            "target_activity": node.get(ANDROID + "targetActivity"),
            "filters": [{
                "actions": [e.get(ANDROID + "name") for e in f.findall("action")],
                "categories": [e.get(ANDROID + "name") for e in f.findall("category")],
                "schemes": [e.get(ANDROID + "scheme") for e in f.findall("data")
                            if e.get(ANDROID + "scheme")],
            } for f in filters],
        }, ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: inspect_manifest.py APK")
    inspect(sys.argv[1])
