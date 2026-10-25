#!/usr/bin/env python3
"""Định cỡ hạ tầng và ước tính chi phí tham khảo cho VOBS theo quy mô và nơi triển khai.

In ra các bảng Markdown dùng trong docs/infrastructure.md; với `--json` in dữ liệu cho trang Hạ tầng của website
(website/data/infrastructure.json). Mọi giả định và đơn giá nằm ở đầu file: ngân hàng sửa
theo tải thực tế, báo giá nhận được hoặc tỷ giá mới rồi chạy lại `python3 scripts/sizing.py`. Chỉ dùng thư viện chuẩn.

Đơn giá là giá niêm yết, chưa gồm VAT, thu thập ngày PRICES_AS_OF; nguồn ghi trong docs/infrastructure.md.
"""
import json
import math
import sys

PRICES_AS_OF = "2026-10-25"
USD_VND = 26_100  # Vietcombank, giá bán USD giữa tháng 10/2026
HOURS_PER_MONTH = 730
HEADROOM = 1.3  # dự phòng cho node hỏng, DaemonSet, phần dành cho hệ thống và tăng tải đột biến
AUDIT_KB_PER_CALL = 1.0  # bản ghi log kiểm toán sau nén
RESPONSE_KB_PER_CALL = 4.0  # dữ liệu trả về cho đối tác, tính băng thông ra Internet
OS_DISK_GB = 100

# Node chuẩn: "app" chạy service không trạng thái, "data" chạy Kafka, PostgreSQL, Valkey, quan sát
NODE = {"app": (8, 32), "data": (8, 64)}

# Thành phần: (tên, pool, số bản, vCPU mỗi bản, RAM GB mỗi bản, đĩa dữ liệu GB mỗi bản)
TIERS = {
    "S": {
        "name": "Nhỏ: thí điểm hoặc ngân hàng nhỏ",
        "peak_tps": 150,
        "calls_per_day": 3_000_000,
        "partners": 30,
        "customers": 2_000_000,
        "components": [
            ("API Gateway (APISIX, Coraza WAF)", "app", 3, 2, 4, 0),
            ("Keycloak", "app", 3, 2, 4, 0),
            ("Quản lý sự đồng ý", "app", 3, 1, 2, 0),
            ("OpenFGA", "app", 3, 1, 2, 0),
            ("Service đọc dữ liệu, payment adapter", "app", 3, 2, 4, 0),
            ("Kafka Connect, Debezium", "app", 2, 2, 8, 0),
            ("OpenBao", "app", 3, 1, 2, 20),
            ("Nền tảng (ingress, cert-manager, Argo CD)", "app", 1, 4, 8, 0),
            ("Kafka (KRaft)", "data", 3, 4, 16, 1_000),
            ("PostgreSQL kho đọc, consent, Keycloak (CloudNativePG)", "data", 3, 4, 32, 1_000),
            ("Valkey", "data", 3, 2, 8, 0),
            ("Quan sát (Prometheus, Loki, Tempo, Grafana)", "data", 3, 2, 8, 1_000),
        ],
        "nonprod": {"app": 6, "data": 3, "disk_gb": 3_000},
    },
    "M": {
        "name": "Vừa: ngân hàng quy mô trung bình",
        "peak_tps": 800,
        "calls_per_day": 20_000_000,
        "partners": 150,
        "customers": 8_000_000,
        "components": [
            ("API Gateway (APISIX, Coraza WAF)", "app", 3, 4, 8, 0),
            ("Keycloak", "app", 3, 4, 8, 0),
            ("Quản lý sự đồng ý", "app", 3, 2, 4, 0),
            ("OpenFGA", "app", 3, 2, 4, 0),
            ("Service đọc dữ liệu, payment adapter", "app", 4, 2, 4, 0),
            ("Kafka Connect, Debezium", "app", 3, 4, 16, 0),
            ("OpenBao", "app", 3, 1, 2, 20),
            ("Nền tảng (ingress, cert-manager, Argo CD)", "app", 1, 8, 16, 0),
            ("Kafka (KRaft)", "data", 3, 8, 32, 2_000),
            ("PostgreSQL kho đọc, consent, Keycloak (CloudNativePG)", "data", 3, 8, 64, 2_000),
            ("Valkey", "data", 3, 4, 16, 0),
            ("Quan sát (Prometheus, Loki, Tempo, Grafana)", "data", 3, 8, 32, 3_000),
        ],
        "nonprod": {"app": 9, "data": 6, "disk_gb": 6_000},
    },
    "L": {
        "name": "Lớn: ngân hàng có tập khách hàng lớn",
        "peak_tps": 3_000,
        "calls_per_day": 80_000_000,
        "partners": 500,
        "customers": 30_000_000,
        "components": [
            ("API Gateway (APISIX, Coraza WAF)", "app", 6, 4, 8, 0),
            ("Keycloak", "app", 4, 4, 8, 0),
            ("Quản lý sự đồng ý", "app", 6, 2, 4, 0),
            ("OpenFGA", "app", 6, 2, 4, 0),
            ("Service đọc dữ liệu, payment adapter", "app", 8, 2, 4, 0),
            ("Kafka Connect, Debezium", "app", 4, 4, 16, 0),
            ("OpenBao", "app", 3, 2, 4, 20),
            ("Nền tảng (ingress, cert-manager, Argo CD)", "app", 1, 16, 32, 0),
            ("Kafka (KRaft)", "data", 5, 8, 64, 4_000),
            ("Kafka controller riêng", "data", 3, 2, 4, 50),
            ("PostgreSQL kho đọc (CloudNativePG)", "data", 3, 16, 128, 4_000),
            ("PostgreSQL consent, Keycloak (CloudNativePG)", "data", 3, 8, 64, 1_000),
            ("Valkey", "data", 6, 4, 32, 0),
            ("Quan sát (Prometheus, Loki, Tempo, Grafana)", "data", 5, 8, 32, 6_000),
        ],
        "nonprod": {"app": 12, "data": 6, "disk_gb": 10_000},
    },
}

# Đơn giá cloud, USD mỗi tháng (730 giờ). commit = cam kết 3 năm, không trả trước; None = chỉ có giá theo yêu cầu
# hoặc phải xin báo giá. k8s = phí control plane có quản lý; None = tự dựng control plane 3 node 4 vCPU/16 GB.
CLOUDS = {
    "AWS (Local Zone Hà Nội)": {
        "app": 0.504 * HOURS_PER_MONTH, "data": 0.6384 * HOURS_PER_MONTH,  # m7i.2xlarge, r7i.2xlarge
        "app_commit": 0.2556 * HOURS_PER_MONTH, "data_commit": 0.33301 * HOURS_PER_MONTH,  # Compute Savings Plan 3 năm
        "disk": 0.096, "egress": 0.12, "k8s": 0.10 * HOURS_PER_MONTH,
        "private_link": 2 * 0.30 * HOURS_PER_MONTH,  # 2 cổng Direct Connect 1 Gbps
        "os_disk_included": False,
    },
    "Azure (Southeast Asia, Singapore)": {
        "app": 0.48 * HOURS_PER_MONTH, "data": 0.608 * HOURS_PER_MONTH,  # D8s v5, E8s v5
        "app_commit": 4_983 / 36, "data_commit": 6_311 / 36,  # Reserved Instance 3 năm
        "disk": 0.0897, "egress": 0.11, "k8s": 0.10 * HOURS_PER_MONTH,
        "private_link": None, "os_disk_included": False,
    },
    "GCP (asia-southeast1, Singapore)": {
        "app": 279.94, "data": 377.62,  # n2-standard-8, n2-highmem-8, đã trừ sustained use discount
        "app_commit": 157.42, "data_commit": 212.37,  # Committed use 3 năm
        "disk": 0.11, "egress": 0.11, "k8s": 0.10 * HOURS_PER_MONTH,
        "private_link": None, "os_disk_included": False,
    },
    "Alibaba Cloud (Singapore)": {
        "app": 0.432 * HOURS_PER_MONTH, "data": 0.5472 * HOURS_PER_MONTH,  # ecs.g7.2xlarge, ecs.r7.2xlarge
        "app_commit": None, "data_commit": None,
        "disk": 0.0924, "egress": 0.081, "k8s": None,
        "private_link": None, "os_disk_included": False,
    },
    "Viettel IDC": {
        "app": 4_100_000 / USD_VND, "data": 7_300_000 / USD_VND,  # T2.Gen 06 (8/32), T2.Mem Burst 03 (8/64)
        "app_commit": None, "data_commit": None,
        "disk": 2_700 / USD_VND, "egress": 0.0, "k8s": None,
        "private_link": None, "os_disk_included": True,
    },
    "CMC Cloud": {
        # Nội suy từ bảng giá node Kubernetes: 150.000 đ mỗi vCPU, 75.000 đ mỗi GB RAM, khoảng 2.700 đ mỗi GB đĩa
        "app": (8 * 150_000 + 32 * 75_000) / USD_VND, "data": (8 * 150_000 + 64 * 75_000) / USD_VND,
        "app_commit": None, "data_commit": None,
        "disk": 2_700 / USD_VND, "egress": 0.0, "k8s": None,
        "private_link": None, "os_disk_included": False,
    },
}
# Tên tiếng Anh cho website
EN = {
    "Nhỏ: thí điểm hoặc ngân hàng nhỏ": "Small: pilot or small bank",
    "Vừa: ngân hàng quy mô trung bình": "Medium: mid-sized bank",
    "Lớn: ngân hàng có tập khách hàng lớn": "Large: bank with a large customer base",
    "API Gateway (APISIX, Coraza WAF)": "API Gateway (APISIX, Coraza WAF)",
    "Keycloak": "Keycloak",
    "Quản lý sự đồng ý": "Consent Management",
    "OpenFGA": "OpenFGA",
    "Service đọc dữ liệu, payment adapter": "Data read services, payment adapter",
    "Kafka Connect, Debezium": "Kafka Connect, Debezium",
    "OpenBao": "OpenBao",
    "Nền tảng (ingress, cert-manager, Argo CD)": "Platform (ingress, cert-manager, Argo CD)",
    "Kafka (KRaft)": "Kafka (KRaft)",
    "Kafka controller riêng": "Dedicated Kafka controllers",
    "PostgreSQL kho đọc, consent, Keycloak (CloudNativePG)": "PostgreSQL read store, consent, Keycloak (CloudNativePG)",
    "PostgreSQL kho đọc (CloudNativePG)": "PostgreSQL read store (CloudNativePG)",
    "PostgreSQL consent, Keycloak (CloudNativePG)": "PostgreSQL consent, Keycloak (CloudNativePG)",
    "Valkey": "Valkey",
    "Quan sát (Prometheus, Loki, Tempo, Grafana)": "Observability (Prometheus, Loki, Tempo, Grafana)",
    "AWS (Local Zone Hà Nội)": "AWS (Hanoi Local Zone)",
    "Azure (Southeast Asia, Singapore)": "Azure (Southeast Asia, Singapore)",
    "GCP (asia-southeast1, Singapore)": "GCP (asia-southeast1, Singapore)",
    "Alibaba Cloud (Singapore)": "Alibaba Cloud (Singapore)",
    "Viettel IDC": "Viettel IDC",
    "CMC Cloud": "CMC Cloud",
}

OBJECT_STORAGE_USD_PER_GB = 0.025  # giả định chung cho mọi cloud: sao lưu và log kiểm toán (WORM)

# On-premise: giá phần cứng là giả định, cần báo giá. Khấu hao 5 năm, giá máy đã gồm bảo hành 5 năm.
ONPREM = {
    # Quy mô S dùng máy 2 x 16 nhân, 256 GB, 4 x 1,92 TB NVMe; M và L dùng máy 2 x 32 nhân, 512 GB, 4 x 3,84 TB NVMe.
    # Giá đã tính mức tăng giá RAM máy chủ 2025-2026.
    "server": {
        "S": {"vcpu": 64, "ram": 256, "nvme_tb": 7.7, "usd": 25_000},
        "M": {"vcpu": 128, "ram": 512, "nvme_tb": 15.4, "usd": 50_000},
        "L": {"vcpu": 128, "ram": 512, "nvme_tb": 15.4, "usd": 50_000},
    },
    "control_plane_server_usd": 12_000,  # 1U, 16 nhân, 64 GB; 3 máy mỗi site
    # Máy lưu trữ đối tượng (Ceph RGW, mã hóa xóa) khi cần trên 20 TB; ít hơn thì đặt trên NVMe của máy tính toán
    "storage_server": {"usable_tb": 100, "usd": 30_000, "from_tb": 20},
    "network_per_site_usd": 40_000,  # 2 switch ToR 25 GbE, switch quản trị, cáp
    "servers_per_rack": 8,  # giới hạn điện 6-8 kW mỗi tủ
    "rack_vnd_per_month": 30_000_000,  # tủ 42U tại trung tâm dữ liệu Tier III, khoảng 20-42 triệu đ
    "usable_cpu": 0.8, "usable_ram": 0.85, "usable_nvme": 0.7,
    "years": 5,
}


def round_up(n, step=3):
    return max(step, math.ceil(n / step) * step)


def size(tier):
    t = TIERS[tier]
    pools = {"app": [0, 0], "data": [0, 0]}
    disk = 0
    for _, pool, replicas, vcpu, ram, data_gb in t["components"]:
        pools[pool][0] += replicas * vcpu
        pools[pool][1] += replicas * ram
        disk += replicas * data_gb
    nodes = {}
    for pool, (vcpu, ram) in pools.items():
        node_vcpu, node_ram = NODE[pool]
        nodes[pool] = round_up(max(vcpu * HEADROOM / node_vcpu, ram * HEADROOM / node_ram))
    pg_gb = sum(r * d for name, _, r, _, _, d in t["components"] if name.startswith("PostgreSQL")) / 3
    audit_gb = t["calls_per_day"] * 365 * AUDIT_KB_PER_CALL / 1e6
    return {
        "requests": pools,
        "nodes": nodes,
        "disk_gb": disk,
        "object_gb": audit_gb + 2 * pg_gb,  # log kiểm toán 1 năm + 2 lần dung lượng CSDL cho sao lưu
        "egress_gb": t["calls_per_day"] * 30 * RESPONSE_KB_PER_CALL / 1e6,
    }


def site_cost(price, nodes, disk_gb, commit):
    app = price["app_commit"] if commit and price["app_commit"] else price["app"]
    data = price["data_commit"] if commit and price["data_commit"] else price["data"]
    n = nodes["app"] + nodes["data"]
    os_disk = 0 if price["os_disk_included"] else n * OS_DISK_GB
    control_plane = price["k8s"] if price["k8s"] is not None else 3 * app / 2
    return nodes["app"] * app + nodes["data"] * data + (disk_gb + os_disk) * price["disk"] + control_plane


def cloud_monthly(tier, price, commit):
    s = size(tier)
    t = TIERS[tier]
    prod = site_cost(price, s["nodes"], s["disk_gb"], commit)
    dr_nodes = {"app": round_up(s["nodes"]["app"] / 2), "data": s["nodes"]["data"]}
    dr = site_cost(price, dr_nodes, s["disk_gb"], commit)
    nonprod = site_cost(price, t["nonprod"], t["nonprod"]["disk_gb"], commit)
    other = s["object_gb"] * OBJECT_STORAGE_USD_PER_GB + s["egress_gb"] * price["egress"] + (price["private_link"] or 0)
    return {"prod": prod, "dr": dr, "nonprod": nonprod, "other": other, "total": prod + dr + nonprod + other}


def onprem(tier):
    s = size(tier)
    o = ONPREM
    server = o["server"][tier]
    vcpu = sum(v for v, _ in s["requests"].values()) * HEADROOM
    ram = sum(r for _, r in s["requests"].values()) * HEADROOM
    need = max(vcpu / (server["vcpu"] * o["usable_cpu"]), ram / (server["ram"] * o["usable_ram"]),
               s["disk_gb"] / 1000 / (server["nvme_tb"] * o["usable_nvme"]))
    prod_servers = max(3, math.ceil(need) + 1)  # N+1
    nonprod_servers = 2 if tier == "S" else 3 if tier == "M" else 4
    object_tb = s["object_gb"] / 1000
    storage_servers = 0
    if object_tb > o["storage_server"]["from_tb"]:
        storage_servers = max(3, math.ceil(object_tb / o["storage_server"]["usable_tb"]))
    sites = {"prod": prod_servers + nonprod_servers, "dr": prod_servers}  # DR dựng bằng prod, môi trường phụ ở site chính
    capex = 0
    racks = 0
    for compute in sites.values():
        capex += compute * server["usd"] + 3 * o["control_plane_server_usd"]
        capex += storage_servers * o["storage_server"]["usd"] + o["network_per_site_usd"]
        racks += math.ceil((compute + 3 + storage_servers) / o["servers_per_rack"])
    colo = racks * o["rack_vnd_per_month"] / USD_VND
    months = o["years"] * 12
    return {
        "prodServers": prod_servers,
        "nonprodServers": nonprod_servers,
        "storageServers": storage_servers,
        "racks": racks,
        "capex": capex,
        "colo": colo,
        "monthly": capex / months + colo,
        "tco": capex + colo * months,
    }


def usd(x):
    return f"{x:,.0f}".replace(",", ".")


def tb(gb):
    return f"{gb / 1000:.1f}".replace(".", ",") + " TB"


def print_components():
    print("## Tài nguyên từng thành phần (số bản × vCPU/RAM, đĩa dữ liệu mỗi bản)\n")
    print("| Thành phần | " + " | ".join(TIERS) + " |")
    print("|---|" + "---|" * len(TIERS))
    names = list(dict.fromkeys(c[0] for t in TIERS.values() for c in t["components"]))
    for name in names:
        cells = []
        for t in TIERS.values():
            spec = next((c for c in t["components"] if c[0] == name), None)
            if spec is None:
                cells.append("")
                continue
            _, _, replicas, vcpu, ram, data_gb = spec
            disk = f", {usd(data_gb)} GB" if data_gb else ""
            cells.append(f"{replicas} × {vcpu}/{ram} GB{disk}")
        print(f"| {name} | " + " | ".join(cells) + " |")


def print_sites():
    print("\n## Định cỡ mỗi site production\n")
    print("| Quy mô | Tải đỉnh | Node app (8 vCPU/32 GB) | Node data (8 vCPU/64 GB) | Tổng vCPU / RAM | "
          "Đĩa SSD dữ liệu | Lưu trữ đối tượng năm đầu | Băng thông ra/tháng |")
    print("|---|---|---|---|---|---|---|---|")
    for tier, t in TIERS.items():
        s = size(tier)
        n = s["nodes"]
        vcpu = n["app"] * NODE["app"][0] + n["data"] * NODE["data"][0]
        ram = n["app"] * NODE["app"][1] + n["data"] * NODE["data"][1]
        print(f"| {tier} | {usd(t['peak_tps'])} TPS | {n['app']} | {n['data']} | {vcpu} vCPU / {usd(ram)} GB | "
              f"{tb(s['disk_gb'])} | {tb(s['object_gb'])} | {tb(s['egress_gb'])} |")


def print_cloud():
    print("\n## Chi phí cloud mỗi tháng (USD): production + DR + phi production\n")
    print("| Nhà cung cấp | S theo yêu cầu | S cam kết 3 năm | M theo yêu cầu | M cam kết 3 năm | "
          "L theo yêu cầu | L cam kết 3 năm |")
    print("|---|---|---|---|---|---|---|")
    for provider, price in CLOUDS.items():
        cells = []
        for tier in TIERS:
            cells.append(usd(cloud_monthly(tier, price, False)["total"]))
            cells.append(usd(cloud_monthly(tier, price, True)["total"]) if price["app_commit"] else "báo giá")
        print(f"| {provider} | " + " | ".join(cells) + " |")

    print("\n## Cơ cấu chi phí AWS cam kết 3 năm (USD/tháng)\n")
    print("| Quy mô | Production | DR | Phi production | Lưu trữ, băng thông, kết nối riêng | Tổng |")
    print("|---|---|---|---|---|---|")
    for tier in TIERS:
        c = cloud_monthly(tier, CLOUDS["AWS (Local Zone Hà Nội)"], True)
        print(f"| {tier} | {usd(c['prod'])} | {usd(c['dr'])} | {usd(c['nonprod'])} | {usd(c['other'])} | "
              f"{usd(c['total'])} |")


def print_onprem():
    print("\n## On-premise (USD)\n")
    print("| Quy mô | Máy chủ tính toán mỗi site | Máy lưu trữ mỗi site | Tủ rack (2 site) | Đầu tư ban đầu | "
          "Thuê chỗ đặt/tháng | Quy ra tháng (khấu hao 5 năm) | Tổng 5 năm |")
    print("|---|---|---|---|---|---|---|---|")
    for tier in TIERS:
        o = onprem(tier)
        print(f"| {tier} | {o['prodServers']} + {o['nonprodServers']} phi production | {o['storageServers']} | {o['racks']} | {usd(o['capex'])} | "
              f"{usd(o['colo'])} | {usd(o['monthly'])} | {usd(o['tco'])} |")


def print_tco():
    print("\n## Tổng chi phí 5 năm, chưa gồm nhân sự (nghìn USD)\n")
    print("| Phương án | S | M | L |")
    print("|---|---|---|---|")
    print("| On-premise (2 site) | " + " | ".join(usd(onprem(t)["tco"] / 1000) for t in TIERS) + " |")
    for provider, price in CLOUDS.items():
        # Giá cam kết 3 năm nếu có, gia hạn cùng mức cho 2 năm cuối; không có thì giá theo yêu cầu
        cells = [usd(cloud_monthly(tier, price, True)["total"] * 60 / 1000) for tier in TIERS]
        print(f"| {provider} | " + " | ".join(cells) + " |")


def as_json():
    """Số liệu cho website; website tự định dạng số theo ngôn ngữ."""
    names = list(dict.fromkeys(c[0] for t in TIERS.values() for c in t["components"]))
    components = []
    for name in names:
        cells = []
        for t in TIERS.values():
            spec = next((c for c in t["components"] if c[0] == name), None)
            cells.append(None if spec is None else dict(zip(("replicas", "vcpu", "ramGb", "diskGb"), spec[2:])))
        components.append({"vi": name, "en": EN[name], "cells": cells})
    tiers = []
    for tier, t in TIERS.items():
        s = size(tier)
        n = s["nodes"]
        tiers.append({
            "id": tier, "vi": t["name"], "en": EN[t["name"]],
            "peakTps": t["peak_tps"], "callsPerDay": t["calls_per_day"],
            "partners": t["partners"], "customers": t["customers"],
            "nodesApp": n["app"], "nodesData": n["data"],
            "vcpu": n["app"] * NODE["app"][0] + n["data"] * NODE["data"][0],
            "ramGb": n["app"] * NODE["app"][1] + n["data"] * NODE["data"][1],
            "diskTb": round(s["disk_gb"] / 1000, 1), "objectTb": round(s["object_gb"] / 1000, 1),
            "egressTb": round(s["egress_gb"] / 1000, 1),
            "onprem": {k: round(v) if isinstance(v, float) else v for k, v in onprem(tier).items()},
        })
    clouds = []
    for provider, price in CLOUDS.items():
        clouds.append({
            "vi": provider, "en": EN[provider],
            "onDemand": [round(cloud_monthly(t, price, False)["total"]) for t in TIERS],
            "commit": [round(cloud_monthly(t, price, True)["total"]) for t in TIERS] if price["app_commit"] else None,
            "tco5y": [round(cloud_monthly(t, price, True)["total"] * 60) for t in TIERS],
        })
    return {"asOf": PRICES_AS_OF, "usdVnd": USD_VND, "tiers": tiers, "components": components, "clouds": clouds}


def main():
    if "--json" in sys.argv[1:]:
        print(json.dumps(as_json(), ensure_ascii=False, indent=2))
        return
    print(f"Giá thu thập ngày {PRICES_AS_OF}; tỷ giá {usd(USD_VND)} đ/USD; giá chưa gồm VAT.\n")
    print_components()
    print_sites()
    print_cloud()
    print_onprem()
    print_tco()


if __name__ == "__main__":
    main()
