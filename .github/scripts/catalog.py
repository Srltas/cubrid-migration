#!/usr/bin/env python3
"""The TC catalog: what build_catalog.py writes and the report scripts read back."""

import json

INVOCATION = "/[test-template-invocation:"


def definition(unique_id):
    """The test case a result belongs to, with any parameterized invocation stripped."""
    return unique_id.partition(INVOCATION)[0]


def load(paths):
    """Every catalog entry, keyed by uniqueId."""
    entries = {}
    for path in paths:
        with open(path, encoding="utf-8") as fh:
            entries.update({e["id"]: e for e in json.load(fh)["entries"]})
    return entries
