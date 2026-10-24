#!/usr/bin/env python3
"""Kiểm tra mọi API trong danh mục Điều 6 Thông tư 64 đều có operation tương ứng trong đặc tả.

Mỗi operation (hoặc webhook) khai báo `x-tt64: { ref: ..., name: ... }`. Script đối chiếu các `ref` với
tt64-catalog.json: thiếu ref nào, hoặc ref lạ không có trong danh mục, đều làm script thoát với mã lỗi 1.
Chỉ dùng thư viện chuẩn để chạy được trong CI mà không cần cài thêm gói.
"""
import json
import re
import sys
from pathlib import Path

SPEC_DIR = Path(__file__).resolve().parent.parent
# Bắt cả dạng khối (`ref: 6.1.b.5`) và dạng inline (`{ ref: 6.1.b.5, name: ... }`); bỏ qua `$ref`
REF = re.compile(r"(?<![$\w])ref:\s*['\"]?(6\.1\.[0-9a-z.]+?)['\"]?\s*[,}\n]")


def main() -> int:
    catalog = json.loads((SPEC_DIR / "tt64-catalog.json").read_text(encoding="utf-8"))
    expected = {api["ref"]: api["name"] for api in catalog["apis"]}

    found = {}
    for spec in sorted((SPEC_DIR / "openapi").glob("*.yaml")):
        for ref in REF.findall(spec.read_text(encoding="utf-8")):
            found.setdefault(ref, []).append(spec.name)

    missing = sorted(set(expected) - set(found))
    unknown = sorted(set(found) - set(expected))
    for ref in missing:
        print(f"THIẾU  {ref}  {expected[ref]}")
    for ref in unknown:
        print(f"LẠ     {ref}  (không có trong tt64-catalog.json) tại {', '.join(found[ref])}")

    covered = len(expected) - len(missing)
    print(f"Độ phủ danh mục Điều 6: {covered}/{len(expected)}")
    return 1 if missing or unknown else 0


if __name__ == "__main__":
    sys.exit(main())
