#!/usr/bin/env python3
"""Tổng hợp kết quả đo tải thành bảng Markdown: report.py THƯ_MỤC_KẾT_QUẢ TARGET_P99_MS."""
import csv
import glob
import json
import os
import statistics
import sys

results, target_p99 = sys.argv[1], float(sys.argv[2])
SERVICES = ["gateway", "keycloak", "consent"]


def cpu_cores(path):
    """Số vCPU trung bình mỗi service dùng trong lần đo, từ các mẫu docker stats."""
    samples = {}
    with open(path, encoding="utf-8") as f:
        for name, percent in csv.reader(f):
            for service in SERVICES:
                if f"-{service}-" in name:
                    samples.setdefault(service, []).append(float(percent.rstrip("%")) / 100)
    return {service: statistics.fmean(values) for service, values in samples.items()}


def verdict(row):
    if row["failed"] > 0.01:
        return "lỗi > 1%"
    if row["dropped"] > 0.01:
        return "không đạt tốc độ"
    if row["p99"] > target_p99:
        return f"p99 > {target_p99:.0f} ms"
    return "đạt"


rows = []
for path in glob.glob(os.path.join(results, "*.json")):
    profile, rate = os.path.basename(path)[:-5].split("-")
    with open(path, encoding="utf-8") as f:
        metrics = json.load(f)
    duration = metrics["http_req_duration"]["values"]
    count = metrics.get("http_reqs", {}).get("values", {}).get("count", 0)
    dropped = metrics.get("dropped_iterations", {}).get("values", {}).get("count", 0)
    row = {
        "profile": profile,
        "rate": int(rate),
        "achieved": metrics.get("iterations", {}).get("values", {}).get("rate", 0),
        "p50": duration["p(50)"],
        "p95": duration["p(95)"],
        "p99": duration["p(99)"],
        "failed": metrics.get("http_req_failed", {}).get("values", {}).get("rate", 0),
        "dropped": dropped / max(count + dropped, 1),
        "cpu": cpu_cores(path[:-5] + ".cpu.csv"),
    }
    row["verdict"] = verdict(row)
    rows.append(row)

print("| Profile | Yêu cầu (lượt/s) | Đạt (lượt/s) | p50 (ms) | p95 (ms) | p99 (ms) | Lỗi | vCPU gateway | vCPU Keycloak "
      "| vCPU consent | Kết quả |")
print("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|")
for r in sorted(rows, key=lambda r: (r["profile"], r["rate"])):
    cpu = [f"{r['cpu'].get(s, 0):.2f}" for s in SERVICES]
    print(f"| {r['profile']} | {r['rate']} | {r['achieved']:.0f} | {r['p50']:.1f} | {r['p95']:.1f} | {r['p99']:.1f} "
          f"| {r['failed'] * 100:.2f}% | {' | '.join(cpu)} | {r['verdict']} |")
