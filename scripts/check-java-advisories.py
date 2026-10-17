#!/usr/bin/env python3
"""Query GitHub's reviewed advisory index for a Maven dependency:list inventory.

Requires an authenticated gh CLI. Exit 0: no reported advisories, 1: findings,
2: incomplete scan. Does not assess exploitability or container OS packages.
"""
import argparse
import concurrent.futures
import json
from pathlib import Path
import re
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("inventory", type=Path)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
coordinates = []
for line in args.inventory.read_text().splitlines():
    match = re.match(r"\s+([^: ]+):([^: ]+):jar:([^: ]+):(compile|runtime)", line)
    if match:
        coordinates.append((match[1] + ":" + match[2], match[3]))
if not coordinates:
    sys.exit("No runtime coordinates found; run Maven dependency:list first.")


def scan(item):
    package, version = item
    result = {"package": package, "version": version}
    try:
        process = subprocess.run(
            ["gh", "api", "-X", "GET", "/advisories", "-f", "ecosystem=maven",
             "-f", f"affects={package}@{version}", "-f", "per_page=100"],
            capture_output=True, text=True, timeout=30, check=True,
        )
        advisories = json.loads(process.stdout)
        if len(advisories) >= 100:
            result["error"] = "Advisory page limit reached; review manually."
        result["advisories"] = [
            {"id": a["ghsa_id"], "severity": a["severity"], "summary": a["summary"]}
            for a in advisories
        ]
    except (subprocess.SubprocessError, OSError, ValueError):
        result["error"] = "Advisory query failed; check gh authorization/network."
    return result


with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    results = list(pool.map(scan, sorted(set(coordinates))))
args.output.write_text(json.dumps(results, indent=2) + "\n")
errors = sum("error" in r for r in results)
findings = sum(bool(r.get("advisories")) for r in results)
print(f"Checked {len(results)} runtime coordinates; {findings} affected packages; {errors} query errors.")
sys.exit(2 if errors else 1 if findings else 0)
