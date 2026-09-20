#!/usr/bin/env python3
import argparse
import csv
import math
import statistics
from pathlib import Path


def percentile(values, q):
    if not values:
        return float("nan")
    xs = sorted(values)
    if len(xs) == 1:
        return float(xs[0])
    pos = (len(xs) - 1) * q
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return float(xs[lo])
    weight = pos - lo
    return xs[lo] * (1.0 - weight) + xs[hi] * weight


def load(path):
    values = []
    wait_values = []
    failures = 0
    with Path(path).open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            raw = (row.get("total_time_ms") or "").strip()
            wait = (row.get("wait_time_ms") or "").strip()
            status = (row.get("status") or "").strip().lower()
            if not raw or not raw.lstrip("-").isdigit() or status not in {"ok", "success"}:
                failures += 1
                continue
            values.append(int(raw))
            if wait.lstrip("-").isdigit():
                wait_values.append(int(wait))
    if not values:
        raise SystemExit(f"No valid TotalTime samples in {path}")
    return values, wait_values, failures


def stats(values):
    return {
        "n": len(values),
        "median": statistics.median(values),
        "mean": statistics.fmean(values),
        "p90": percentile(values, 0.90),
        "p95": percentile(values, 0.95),
        "min": min(values),
        "max": max(values),
        "stdev": statistics.stdev(values) if len(values) > 1 else 0.0,
    }


def fmt(v):
    return f"{v:.1f}"


def main():
    p = argparse.ArgumentParser(description="Compare manual Android startup A/B CSV files.")
    p.add_argument("a_csv")
    p.add_argument("b_csv")
    p.add_argument("--output", "-o")
    args = p.parse_args()

    a_values, a_wait, a_fail = load(args.a_csv)
    b_values, b_wait, b_fail = load(args.b_csv)
    a = stats(a_values)
    b = stats(b_values)

    delta = b["median"] - a["median"]
    pct = (delta / a["median"] * 100.0) if a["median"] else float("nan")

    lines = [
        "# Manual Baseline Profile startup A/B",
        "",
        "| Metric | A: verify / profiles cleared | B: packaged profile / speed-profile | Delta B-A |",
        "| --- | ---: | ---: | ---: |",
        f"| Valid samples | {a['n']} | {b['n']} | — |",
        f"| Median TotalTime | {fmt(a['median'])} ms | {fmt(b['median'])} ms | {fmt(delta)} ms ({pct:+.1f}%) |",
        f"| Mean TotalTime | {fmt(a['mean'])} ms | {fmt(b['mean'])} ms | {fmt(b['mean'] - a['mean'])} ms |",
        f"| p90 TotalTime | {fmt(a['p90'])} ms | {fmt(b['p90'])} ms | {fmt(b['p90'] - a['p90'])} ms |",
        f"| p95 TotalTime | {fmt(a['p95'])} ms | {fmt(b['p95'])} ms | {fmt(b['p95'] - a['p95'])} ms |",
        f"| Min / Max | {a['min']} / {a['max']} ms | {b['min']} / {b['max']} ms | — |",
        f"| Sample stdev | {fmt(a['stdev'])} ms | {fmt(b['stdev'])} ms | — |",
        f"| Invalid/failed samples | {a_fail} | {b_fail} | — |",
        "",
        "Interpretation:",
        "",
        "- Negative B-A values mean the Baseline Profile state was faster.",
        "- This is a manual same-device startup comparison, not a Macrobenchmark result.",
        "- Keep the candidate only when the distribution shifts consistently enough to exceed ordinary run-to-run noise.",
    ]

    if a_wait and b_wait:
        aw = stats(a_wait)
        bw = stats(b_wait)
        lines += [
            "",
            f"Median WaitTime: A {fmt(aw['median'])} ms; B {fmt(bw['median'])} ms; delta {fmt(bw['median'] - aw['median'])} ms.",
        ]

    text = "\n".join(lines) + "\n"
    if args.output:
        Path(args.output).write_text(text, encoding="utf-8")
    print(text, end="")


if __name__ == "__main__":
    main()
