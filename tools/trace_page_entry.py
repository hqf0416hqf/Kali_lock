#!/usr/bin/env python3
"""Trace one page-navigation request using lifecycle events, never unlock.

--cold ends ONLY the target app process before navigation, retaining app data.
Use while the phone is idle/unlocked and no door operation is in progress.
Raw log records, intent URIs, tokens and task identifiers are never saved.
"""
import argparse
import json
from pathlib import Path
import queue
import re
import subprocess
import threading
import time
from urllib.parse import urlencode

ROOT = Path(__file__).resolve().parents[1]
TARGET = "com.tmall.campus.and"
NAMES = {
    "com.tmall.campus.launcher.SplashActivity": "SPLASH",
    "com.tmall.campus.launcher.HotSplashActivity": "HOT_SPLASH",
    "com.tmall.campus.and.main.MainActivity": "HOME",
    "com.tmall.campus.bizwebview.ui.CampusWebActivity": "WEB_CONTAINER",
    "com.tmall.campus.and.dp.DispatchActivity": "DISPATCH",
    "com.tmall.campus.route.SchemeFilterActivity": "DIRECT_ROUTER",
}
TAGS = ["wm_new_intent", "wm_create_activity", "wm_restart_activity", "wm_resume_activity",
        "wm_set_resumed_activity", "wm_on_resume_called", "wm_on_create_called", "wm_on_start_called"]


def adb(args, timeout=10):
    return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--entry", choices=["direct", "compatible", "launcher"], required=True)
    parser.add_argument("--cold", action="store_true")
    args = parser.parse_args()
    policy = adb(["shell", "dumpsys", "window", "policy"]).stdout
    unlocked = re.search(r"\bshowing=(true|false)", policy)
    if not unlocked or unlocked.group(1) != "false":
        raise SystemExit("Skip: phone locked or lock state unknown")
    since = adb(["shell", "date", "+%s.%3N"]).stdout.strip()
    if not re.fullmatch(r"\d+\.\d+", since):
        raise SystemExit("Unsupported device time format; nothing launched")
    command = ["adb", "logcat", "-b", "events", "-v", "brief", "-T", since]
    collector = subprocess.Popen(command + [t + ":I" for t in TAGS] + ["*:S"],
                                 stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    pending = queue.Queue()
    started = time.monotonic()

    def read():
        for line in collector.stdout:
            match = re.search(r"^[VDIWEF]/(" + "|".join(TAGS) + r")\s*\(", line)
            if not match:
                continue
            for name, label in NAMES.items():
                short = TARGET + "/" + (name[len(TARGET):] if name.startswith(TARGET + ".") else name)
                if re.search(r"(?<![\w.])" + re.escape(name) + r"(?![\w.$])", line) or short in line:
                    pending.put({"elapsed_s": round(time.monotonic() - started, 3),
                                 "event": match.group(1), "page": label})
                    break

    threading.Thread(target=read, daemon=True).start()
    records = []
    try:
        time.sleep(0.2)
        if args.cold:
            adb(["shell", "am", "force-stop", TARGET])
        page = "https://biz.confong.cn/app/tmall-xiaoyuan/page-m-webview/doorLock?loginRequired=true&hideNavigator=true"
        query = urlencode({"url": page})
        if args.entry == "launcher":
            launch = ["shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
                      "-n", "local.dorm.shortcut/.SetupActivity"]
        elif args.entry == "direct":
            component = TARGET + "/com.tmall.campus.route.SchemeFilterActivity"
            action, link = "com.manu.route", "/web/open?" + query
        else:
            component = TARGET + "/com.tmall.campus.and.dp.DispatchActivity"
            action, link = "android.intent.action.VIEW", "tmallcampus://web/open?" + query
        if args.entry != "launcher":
            launch = ["shell", "am", "start", "-W", "-a", action, "-n", component, "-d", link]
        result = adb(launch, timeout=20)
        print("NAVIGATION_REQUEST_RETURNED", flush=True)
        for line in result.stdout.splitlines():
            if line.startswith(("Status:", "LaunchState:", "TotalTime:", "WaitTime:")):
                print(line, flush=True)
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            try:
                record = pending.get(timeout=0.3)
                records.append(record); print(json.dumps(record), flush=True)
            except queue.Empty:
                pass
    finally:
        collector.terminate()
        try:
            collector.wait(timeout=3)
        except subprocess.TimeoutExpired:
            collector.kill(); collector.wait(timeout=3)
    output = ROOT / "private" / ("lifecycle-" + args.entry + ("-cold-" if args.cold else "-warm-")
                                + time.strftime("%Y%m%d-%H%M%S") + ".json")
    with output.open("x") as handle:
        json.dump({"entry": args.entry, "cold": args.cold, "events": records,
                   "no_unlock_input": True, "note": "Lifecycle events do not prove visible pixel frames."},
                  handle, ensure_ascii=False, indent=2)
    output.chmod(0o600)
    print("LOCAL_REPORT=" + str(output))
    print("HOME_OR_SPLASH_EVENT_OBSERVED=" + str(any(r["page"] in {"HOME", "SPLASH", "HOT_SPLASH"} for r in records)))


if __name__ == "__main__":
    main()
