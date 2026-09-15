#!/usr/bin/env python3
"""Turn JUnit Open Test Report XML into the TC catalog the status page renders.

The same report format is emitted by a dry run (what is defined) and by a real run (what was
executed), so both sides share junit:uniqueId and the page can join them without matching names.
"""

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET

NS = {
    "c": "https://schemas.opentest4j.org/reporting/core/0.2.0",
    "e": "https://schemas.opentest4j.org/reporting/events/0.2.0",
    "java": "https://schemas.opentest4j.org/reporting/java/0.2.0",
    "junit": "https://schemas.junit.org/open-test-reporting",
}

SOURCE_ROOT = {
    "unit": "tests/unit-test/src/test/java",
    "e2e": "tests/e2e/src/test/java",
}

E2E_SCENARIO = re.compile(r"\.tests\.migration\.(?P<source>[^.]+)\.(?P<cls>\w+?)To(?P<target>\w+?)Test")


def read_nodes(path):
    """Every started event, keyed by its report id, with its parent."""
    root = ET.parse(path).getroot()
    nodes = {}
    for started in root.findall("e:started", NS):
        meta = started.find("c:metadata", NS)
        if meta is None:
            continue
        uid = meta.find("junit:uniqueId", NS)
        if uid is None:
            continue
        method = started.find("c:sources/java:methodSource", NS)
        nodes[started.get("id")] = {
            "parent": started.get("parentId"),
            "name": started.get("name"),
            "uniqueId": uid.text,
            "class": method.get("className") if method is not None else None,
            "method": method.get("methodName") if method is not None else None,
        }
    return nodes


def group_chain(nodes, node_id):
    """Display names from the engine down to, but excluding, the test itself."""
    chain = []
    cur = nodes[node_id]["parent"]
    while cur in nodes:
        chain.append(nodes[cur]["name"])
        cur = nodes[cur]["parent"]
    return list(reversed(chain))[1:]  # drop the engine node


def kind_of(unique_id):
    if "[test-template-invocation:" in unique_id:
        return None  # an execution of a definition, not a definition
    if "[test-template:" in unique_id:
        return "parameterized"
    if "[method:" in unique_id:
        return "test"
    return None


def source_url(repo, commit, module, class_name):
    top = class_name.split("$")[0]
    return f"https://github.com/{repo}/blob/{commit}/{SOURCE_ROOT[module]}/{top.replace('.', '/')}.java"


def e2e_scenario(class_name):
    hit = E2E_SCENARIO.search(class_name)
    if hit:
        return {"source": hit.group("source"), "target": hit.group("target").lower()}
    if ".tests.cli." in class_name:
        return {"source": None, "target": "console"}
    return None


def definitions(path, module, repo, commit):
    nodes = read_nodes(path)
    out = {}
    for node_id, node in nodes.items():
        kind = kind_of(node["uniqueId"])
        if kind is None or node["class"] is None:
            continue
        entry = {
            "id": node["uniqueId"],
            "type": module,
            "name": node["name"],
            "group": group_chain(nodes, node_id),
            "class": node["class"],
            "method": node["method"],
            "kind": kind,
            "sourceUrl": source_url(repo, commit, module, node["class"]),
        }
        if module == "e2e":
            scenario = e2e_scenario(node["class"])
            if scenario:
                entry["e2e"] = scenario
        out[node["uniqueId"]] = entry
    return out


def selecting_job(specs):
    """uniqueId -> the CI job whose -Dtest pattern selects it."""
    owner = {}
    for spec in specs:
        job, _, path = spec.partition("=")
        for node in read_nodes(path).values():
            if kind_of(node["uniqueId"]):
                owner.setdefault(node["uniqueId"], job)
    return owner


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--type", required=True, choices=sorted(SOURCE_ROOT))
    ap.add_argument("--repo", required=True)
    ap.add_argument("--commit", required=True)
    ap.add_argument("--all", required=True, help="dry-run report covering every test in the module")
    ap.add_argument(
        "--selected",
        action="append",
        default=[],
        metavar="JOB=OTR_XML",
        help="dry-run report for one CI job's -Dtest pattern; repeat per job. "
        "Omit when a single job runs the whole module.",
    )
    ap.add_argument("--ci-job", help="job that runs the whole module, when --selected is not used")
    ap.add_argument("-o", "--out", required=True)
    args = ap.parse_args()

    entries = definitions(args.all, args.type, args.repo, args.commit)
    if not entries:
        sys.exit(f"{args.all}: no test definitions found — refusing to write an empty catalog")

    if args.selected:
        owner = selecting_job(args.selected)
        for entry in entries.values():
            entry["ciJob"] = owner.get(entry["id"])
            entry["ciExecuted"] = entry["ciJob"] is not None
    else:
        for entry in entries.values():
            entry["ciJob"] = args.ci_job
            entry["ciExecuted"] = args.ci_job is not None

    rows = sorted(entries.values(), key=lambda e: e["id"])
    catalog = {
        "schemaVersion": 1,
        "type": args.type,
        "commit": args.commit,
        "sourceRoot": SOURCE_ROOT[args.type],
        "counts": {
            "defined": len(rows),
            "test": sum(1 for r in rows if r["kind"] == "test"),
            "parameterized": sum(1 for r in rows if r["kind"] == "parameterized"),
            "notCiExecuted": sum(1 for r in rows if not r["ciExecuted"]),
        },
        "entries": rows,
    }
    assert catalog["counts"]["defined"] == len(catalog["entries"])
    assert catalog["counts"]["test"] + catalog["counts"]["parameterized"] == len(rows)

    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(catalog, fh, indent=2, ensure_ascii=False, sort_keys=False)
        fh.write("\n")
    print(
        f"{args.out}: {catalog['counts']['defined']} definitions "
        f"({catalog['counts']['test']} test, {catalog['counts']['parameterized']} parameterized, "
        f"{catalog['counts']['notCiExecuted']} not run by CI)"
    )


if __name__ == "__main__":
    main()
