#!/usr/bin/env python3
"""Join one run's test results onto the TC catalog and decide what the status page may claim.

Results and the catalog are both Open Test Reports, so they join on junit:uniqueId rather than on
display names. Absence is never success: a suite that reported nothing is NO_DATA, and a suite whose
job went green without executing a single test is EMPTY.
"""

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter

NS = {
    "c": "https://schemas.opentest4j.org/reporting/core/0.2.0",
    "e": "https://schemas.opentest4j.org/reporting/events/0.2.0",
    "junit": "https://schemas.junit.org/open-test-reporting",
}

# A definition, not one of its parameterized executions.
def is_definition(unique_id):
    if "[test-template-invocation:" in unique_id:
        return False
    return "[test-template:" in unique_id or "[method:" in unique_id


def read_results(path):
    """uniqueId -> outcome, plus the uniqueIds of containers that failed outright."""
    root = ET.parse(path).getroot()
    by_id, uid_of = {}, {}
    for started in root.findall("e:started", NS):
        meta = started.find("c:metadata", NS)
        if meta is None:
            continue
        uid = meta.find("junit:uniqueId", NS)
        if uid is not None:
            uid_of[started.get("id")] = uid.text

    outcomes, blocked_roots = {}, []
    for finished in root.findall("e:finished", NS):
        uid = uid_of.get(finished.get("id"))
        if uid is None:
            continue
        result = finished.find("c:result", NS)
        status = result.get("status") if result is not None else "UNKNOWN"
        if is_definition(uid):
            outcomes[uid] = status
        elif status in ("FAILED", "ABORTED"):
            # A class or @Nested group that blew up in setup: its tests never ran.
            blocked_roots.append(uid)
    return outcomes, blocked_roots


TC_STATE = {"SUCCESSFUL": "passed", "FAILED": "failed", "ABORTED": "failed", "SKIPPED": "skipped"}


def suite_state(job_conclusion, present, counts):
    if job_conclusion == "cancelled":
        return "CANCELLED"
    if not present:
        return "NO_DATA"
    if counts["blocked"]:
        return "BLOCKED"
    if counts["failed"]:
        return "FAILED"
    executed = counts["passed"] + counts["failed"] + counts["skipped"] + counts["blocked"]
    if executed == 0:
        return "EMPTY"
    if counts["passed"] == 0:
        return "EMPTY"
    return "PASSED_WITH_SKIPS" if counts["skipped"] else "PASSED"


def run_state(suites):
    """PASSED is reserved for a run where every expected suite reported and passed."""
    states = {s["state"] for s in suites}
    if not states or states <= {"NO_DATA"}:
        return "NO_DATA"
    if "FAILED" in states:
        return "FAILED"
    if "BLOCKED" in states or "EMPTY" in states:
        return "BLOCKED"
    if "CANCELLED" in states:
        return "CANCELLED"
    if "NO_DATA" in states:
        return "INCOMPLETE"
    return "PASSED"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--catalog", action="append", required=True)
    ap.add_argument("--result", action="append", default=[], metavar="SUITE=OTR_XML")
    ap.add_argument("--jobs", required=True, help='JSON array of {"name":…,"conclusion":…}')
    ap.add_argument("--expect", action="append", required=True, help="suite name CI must report")
    ap.add_argument("--commit", required=True)
    ap.add_argument("--run-id", required=True)
    ap.add_argument("--run-attempt", required=True)
    ap.add_argument("--run-url", required=True)
    ap.add_argument("--run-started-at", required=True)
    ap.add_argument("-o", "--out", required=True)
    args = ap.parse_args()

    entries = []
    for path in args.catalog:
        entries.extend(json.load(open(path, encoding="utf-8"))["entries"])
    if not entries:
        sys.exit("catalog is empty — refusing to publish a status for an unknown test set")
    catalog = {e["id"]: e for e in entries}

    results, blocked_roots = {}, {}
    for spec in args.result:
        suite, _, path = spec.partition("=")
        results[suite], blocked_roots[suite] = read_results(path)

    jobs = {j["name"]: j.get("conclusion") for j in json.load(open(args.jobs, encoding="utf-8"))}

    # Which suite is a TC's? Unit TCs belong to the unit job; an e2e TC belongs to whichever
    # suite reported it, so a TC no suite reported stays unattributed on purpose.
    by_tc = {}
    for suite, outcomes in results.items():
        for uid, status in outcomes.items():
            if uid in catalog:
                by_tc[uid] = {"suite": suite, "state": TC_STATE.get(status, "failed")}
    for suite, roots in blocked_roots.items():
        for uid, entry in catalog.items():
            if uid in by_tc:
                continue
            if any(uid.startswith(root + "/") for root in roots):
                by_tc[uid] = {"suite": suite, "state": "blocked"}

    for uid, entry in catalog.items():
        if uid in by_tc:
            continue
        job = entry.get("ciJob")
        if not entry.get("ciExecuted", True) or job is None:
            by_tc[uid] = {"suite": None, "state": "not-in-ci"}
        elif job in results:
            # Its job reported, and this TC was not among what it ran.
            by_tc[uid] = {"suite": job, "state": "not-selected"}
        else:
            by_tc[uid] = {"suite": job, "state": "no-report"}

    suites = []
    for name in args.expect:
        counts = Counter(
            tc["state"] for tc in by_tc.values() if tc["suite"] == name
        )
        counts = {k: counts.get(k, 0) for k in ("passed", "failed", "skipped", "blocked")}
        suites.append(
            {
                "name": name,
                "jobConclusion": jobs.get(name),
                "reported": name in results,
                "state": suite_state(jobs.get(name), name in results, counts),
                "counts": counts,
            }
        )

    tallies = Counter(tc["state"] for tc in by_tc.values())
    status = {
        "schemaVersion": 1,
        "commit": args.commit,
        "run": {
            "id": args.run_id,
            "attempt": args.run_attempt,
            "url": args.run_url,
            "startedAt": args.run_started_at,
        },
        "state": run_state(suites),
        "coverage": {
            "expected": len(args.expect),
            "reported": sum(1 for s in suites if s["reported"]),
        },
        "counts": {
            "defined": len(catalog),
            "executed": sum(len(o) for o in results.values()),
            "byState": dict(sorted(tallies.items())),
        },
        "suites": suites,
        "byTest": {uid: tc["state"] for uid, tc in sorted(by_tc.items())},
    }
    status["coverage"]["incomplete"] = (
        status["coverage"]["reported"] < status["coverage"]["expected"]
    )

    assert len(status["byTest"]) == len(catalog)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(status, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    print(
        f"{args.out}: run {status['state']}, "
        f"{status['coverage']['reported']}/{status['coverage']['expected']} suites reported, "
        f"{status['counts']['byState']}"
    )


if __name__ == "__main__":
    main()
