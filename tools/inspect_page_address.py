#!/usr/bin/env python3
"""Read address STRUCTURE from the target app's already-enabled WebView debugger.

No JavaScript evaluation, page contents, cookies, storage or remote requests.
Temporarily forwards an allocated loopback port, removes only that forward.
Only sanitized metadata is saved to private/; raw URLs are never persisted.
"""
import json
from pathlib import Path
import re
import subprocess
import time
import urllib.request
from urllib.parse import urlsplit, parse_qsl, unquote

ROOT = Path(__file__).resolve().parents[1]


def adb(args):
    return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=10, check=True)


def safe_path(path):
    result = []
    previous = ""
    for part in path.split("/"):
        decoded = unquote(part)
        if (previous.lower() in {"user", "users", "account", "door", "doors", "device", "devices", "room", "rooms"}
                or re.search(r"\d{4,}|[a-fA-F0-9]{16,}|@", decoded)
                or len(decoded) > 48):
            result.append("{redacted}")
        else:
            result.append(part)
        previous = decoded
    return "/".join(result)


def safe_keys(query):
    return sorted(set(key if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_.-]{0,40}", key)
                      else "{redacted-key}" for key, value in parse_qsl(query, keep_blank_values=True)))


def structure(raw):
    parsed = urlsplit(raw)
    if parsed.scheme not in {"https", "http", "tmallcampus"}:
        return {"scheme": parsed.scheme or "empty", "url_content_omitted": True}
    fragment = urlsplit(parsed.fragment)
    return {"scheme": parsed.scheme, "host": parsed.hostname,
            "path_template": safe_path(parsed.path), "query_keys_only": safe_keys(parsed.query),
            "fragment_path_template": safe_path(fragment.path),
            "fragment_query_keys_only": safe_keys(fragment.query)}


def main():
    pids = adb(["shell", "pidof", "com.tmall.campus.and"]).stdout.split()
    sockets = adb(["shell", "cat", "/proc/net/unix"]).stdout
    name = next(("webview_devtools_remote_" + pid for pid in pids
                 if re.search(r"@webview_devtools_remote_" + re.escape(pid) + r"\b", sockets)), None)
    if name is None:
        raise SystemExit("No target debugger socket. Nothing read; do not enable debug/root to force it.")
    port = None
    try:
        port = adb(["forward", "tcp:0", "localabstract:" + name]).stdout.strip()
        if not re.fullmatch(r"\d+", port):
            raise RuntimeError("Unexpected forward result")
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open("http://127.0.0.1:" + port + "/json/list", timeout=10) as response:
            pages = json.load(response)
        records = []
        for page in pages:
            item = structure(page.get("url", ""))
            if item not in records:
                records.append(item)
        folder = ROOT / "private"
        folder.mkdir(mode=0o700, exist_ok=True)
        output = folder / ("page-address-structure-" + time.strftime("%Y%m%d-%H%M%S") + ".json")
        with output.open("x") as handle:
            json.dump(records, handle, ensure_ascii=False, indent=2)
        output.chmod(0o600)
        print(json.dumps(records, ensure_ascii=False, indent=2))
        print("LOCAL_REPORT=" + str(output))
    finally:
        if port and re.fullmatch(r"\d+", port):
            adb(["forward", "--remove", "tcp:" + port])
            print("Temporary forwarding removed")


if __name__ == "__main__":
    main()
