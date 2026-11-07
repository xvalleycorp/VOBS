#!/usr/bin/env python3
"""Sinh cấu hình gateway cho đo tải từ cấu hình của sandbox: gateway_config.py NGUỒN ĐÍCH cached|nocache.

Bỏ giới hạn tần suất, chuyển API đọc sang upstream giả lập, đặt cách giữ kết quả introspection:
- cached: giữ 30 giây như cấu hình mẫu trong vobs-gateway/apisix/apisix.yaml;
- nocache: mọi request đều hỏi Keycloak, trường hợp xấu nhất.
"""
import re
import sys

source, target, profile = sys.argv[1:4]
INTROSPECTION = {
    "cached": "introspection_interval: 30",
    "nocache": "introspection_interval: 1\n        introspection_cache_ignore: true",
}
with open(source, encoding="utf-8") as f:
    config = f.read()
config, routes = re.subn(r"^( +)introspection_interval: 1$", lambda m: m.group(1) + INTROSPECTION[profile], config,
                         flags=re.M)
config, limits = re.subn(r"^( +)count: \d+$", r"\g<1>count: 1000000000", config, flags=re.M)
config, upstreams = re.subn(r'"microcks:8080"', '"load-upstream:8080"', config)
if not (routes and limits and upstreams):
    sys.exit(f"Cấu hình sandbox đã đổi, không sửa được: {routes} route, {limits} giới hạn, {upstreams} upstream")
# Ghi đè nội dung, không tạo file mới: gateway mount đúng file này
with open(target, "w", encoding="utf-8") as f:
    f.write(config)
