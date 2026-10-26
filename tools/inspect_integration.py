#!/usr/bin/env python3
"""Offline APK structure inspection; no device, network, literal strings or keys.

PYTHONPATH=/tmp/dorm-door-analysis-python python3 tools/inspect_integration.py APK
Use --class-prefix CLASS to inspect declared method signatures and invoke targets.
This reports static reachability clues, never runtime authorization or success.
"""
import argparse
import json
import re
import zipfile

from loguru import logger
from androguard.core.axml import AXMLPrinter
from androguard.core.dex import DEX

ANDROID = '{http://schemas.android.com/apk/res/android}'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    parser.add_argument('--class-prefix', action='append', default=[])
    parser.add_argument('--dex', action='append', default=[],
                        help='Inspect only these known DEX entries; empty means all')
    args = parser.parse_args()
    logger.remove()
    result = {'components': [], 'candidates': [], 'classes': []}
    with zipfile.ZipFile(args.apk) as archive:
        root = AXMLPrinter(archive.read('AndroidManifest.xml')).get_xml_obj()
        app = root.find('application')
        for kind in ('activity', 'activity-alias', 'service', 'receiver', 'provider'):
            for node in app.findall(kind):
                filters = node.findall('intent-filter')
                explicit = node.get(ANDROID + 'exported')
                # Target is API 31+; absent exported plus filters needs review.
                if explicit != 'true' and not (explicit is None and filters):
                    continue
                result['components'].append({
                    'kind': kind, 'name': node.get(ANDROID + 'name'),
                    'exported_explicit': explicit,
                    'enabled_explicit': node.get(ANDROID + 'enabled'),
                    'permission': node.get(ANDROID + 'permission', app.get(ANDROID + 'permission')),
                    'read_permission': node.get(ANDROID + 'readPermission'),
                    'write_permission': node.get(ANDROID + 'writePermission'),
                    'actions': [a.get(ANDROID + 'name') for f in filters for a in f.findall('action')],
                })
        for entry in archive.namelist():
            if not re.fullmatch(r'classes\d*\.dex', entry):
                continue
            if args.dex and entry not in args.dex:
                continue
            dex = DEX(archive.read(entry))
            for cls in dex.get_classes():
                name = cls.get_name()
                if name.startswith('Lcom/tmall/campus/') and re.search(
                    r'(login|auth|session|account|mtop|schooljsbridge|unlock|door)', name, re.I
                ):
                    result['candidates'].append({'dex': entry, 'name': name})
                if not any(name.startswith(p) for p in args.class_prefix):
                    continue
                methods = []
                for method in cls.get_methods():
                    calls = []
                    for ins in method.get_instructions():
                        if ins.get_name().startswith('invoke-'):
                            # Method operands only, not const-string/field values.
                            calls.append(ins.get_output())
                    methods.append({'name': method.get_name(), 'descriptor': method.get_descriptor(),
                                    'access': method.get_access_flags_string(),
                                    'calls': sorted(set(calls))})
                result['classes'].append({'dex': entry, 'name': name, 'methods': methods})
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
