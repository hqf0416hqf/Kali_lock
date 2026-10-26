#!/usr/bin/env python3
"""Passive, finite observation of ONE user-operated normal flow.

No taps, radio commands, packet capture, credential extraction or raw log files.
Run only after the user is at the door and explicitly ready; do not repeat opening.
Times are host receipt/observation times, not exact physical event times.
"""
import argparse
import json
from pathlib import Path
import queue
import re
import subprocess
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
TAGS = ["BluetoothGatt", "BluetoothLeScanner", "NfcAdapter", "NfcActivityManager"]
EVENTS = ["onClientConnectionState", "onConnectionUpdated", "onSearchComplete",
          "onServicesDiscovered", "onCharacteristicWrite", "onCharacteristicRead",
          "onCharacteristicChanged", "onDescriptorWrite", "onScanFailed",
          "onScannerRegistered", "startScan", "stopScan", "connectGatt", "disconnect", "close"]
PAGES = {"com.tmall.campus.launcher.SplashActivity": "SPLASH",
         "com.tmall.campus.launcher.HotSplashActivity": "HOT_SPLASH",
         "com.tmall.campus.and.main.MainActivity": "HOME",
         "com.tmall.campus.bizwebview.ui.CampusWebActivity": "WEB_CONTAINER",
         "com.tmall.campus.nfc.NfcDispatchActivity": "NFC_DISPATCH"}


def adb(args):
    try:
        return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=5)
    except subprocess.TimeoutExpired:
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seconds", type=int, default=90)
    args = parser.parse_args()
    if not 1 <= args.seconds <= 180:
        parser.error("Use a short window of 1–180 seconds")
    folder = ROOT / "private"; folder.mkdir(mode=0o700, exist_ok=True)
    output = folder / ("observation-" + time.strftime("%Y%m%d-%H%M%S") + ".jsonl")
    started = time.monotonic()
    events = queue.Queue()
    collector = None
    with output.open("x") as handle:
        output.chmod(0o600)

        def emit(kind, **fields):
            record = {"elapsed_s": round(time.monotonic() - started, 2), "event": kind, **fields}
            line = json.dumps(record, ensure_ascii=False)
            handle.write(line + "\n"); handle.flush(); print(line, flush=True)

        emit("observation_started", note="No input actions; initial operation may already be in progress.")
        process = adb(["shell", "pidof", "com.tmall.campus.and"])
        pids = process.stdout.split() if process and process.returncode == 0 else []
        if pids:
            date = adb(["shell", "date", "+%m-%d %H:%M:%S.000"])
            since = date.stdout.strip() if date and date.returncode == 0 else "1"
            command = ["adb", "logcat", "-b", "main", "--pid=" + pids[0], "-v", "brief", "-T", since]
            collector = subprocess.Popen(command + [tag + ":V" for tag in TAGS] + ["*:S"],
                                         stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)

            def read_events():
                for line in collector.stdout:
                    match = re.search(r"^[VDIWEF]/(" + "|".join(TAGS) + r")\s*\(", line)
                    if not match:
                        continue
                    for event in EVENTS:
                        if re.search(r"\b" + event + r"\b", line):
                            events.put((match.group(1), event)); break

            threading.Thread(target=read_events, daemon=True).start()
            emit("target_process_log_observation_attached")
        else:
            emit("target_process_not_found")
        last = None
        try:
            while time.monotonic() - started < args.seconds:
                process = adb(["shell", "dumpsys", "activity", "activities"])
                state = "UNKNOWN"
                if process and process.returncode == 0:
                    components = []
                    for line in process.stdout.splitlines():
                        if "mResumedActivity:" in line or "topResumedActivity=" in line:
                            components += re.findall(r"\b[a-zA-Z][\w.]*/[\w.$]+", line)
                    target = [c.split("/", 1)[1] for c in components if c.startswith("com.tmall.campus.and/")]
                    state = PAGES.get(target[-1], "OTHER_TARGET_PAGE") if target else "TARGET_NOT_FOREGROUND"
                if state != last:
                    emit("foreground_state", state=state); last = state
                while True:
                    try:
                        tag, event = events.get_nowait()
                        emit("radio_event", source=tag, category=event)
                    except queue.Empty:
                        break
                time.sleep(0.7)
        finally:
            if collector:
                collector.terminate()
                try:
                    collector.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    collector.kill(); collector.wait(timeout=3)
            emit("observation_finished", note="No events does not mean no radio activity. Physical outcome requires user report.")
    print("LOCAL_REPORT=" + str(output))


if __name__ == "__main__":
    main()
